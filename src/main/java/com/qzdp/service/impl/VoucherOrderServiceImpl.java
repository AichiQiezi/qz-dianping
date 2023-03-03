package com.qzdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.qzdp.dto.Result;
import com.qzdp.entity.VoucherOrder;
import com.qzdp.mapper.VoucherOrderMapper;
import com.qzdp.service.ISeckillVoucherService;
import com.qzdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qzdp.utils.RedisIdWorker;
import com.qzdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static com.qzdp.utils.RedisConstants.SECKILL_TIME_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *

 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    private final ThreadPoolExecutor pool;

    private static final DefaultRedisScript<Long> DESKILL_SCRIPT;

    static {
        DESKILL_SCRIPT = new DefaultRedisScript<>();
        DESKILL_SCRIPT.setLocation(new ClassPathResource("secKill.lua"));
        DESKILL_SCRIPT.setResultType(Long.class);
    }

    public VoucherOrderServiceImpl(ThreadPoolExecutor pool) {
        this.pool = pool;
    }

    /**
     * 利用线程池来接收消息队列的消息来进行创建订单
     */
    @PostConstruct
    private void init() {
        pool.submit(new VoucherOrderHandler());
    }

    /**
     * 要保证接口
     *
     * @param voucherId 优惠卷id
     * @return 返回秒杀结果
     */
    @Override
    public Result secKillVoucher(Long voucherId) {

        //判断当前这个秒杀请求是否在活动时间区间内(效验时间的合法性)
        long curTime = System.currentTimeMillis() / 1000;
        String secTime = stringRedisTemplate.opsForValue().get(SECKILL_TIME_KEY + voucherId);
        String[] s = secTime.split("-");
        long beginTime = Long.parseLong(s[0]);
        long endTime = Long.parseLong(s[1]);

        if (curTime < beginTime || curTime > endTime) {
            return Result.fail("秒杀失败！");
        }

        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        //执行lua脚本 ，通过脚本来执行扣减库存，传递下单消息等
        Long res = stringRedisTemplate.execute(
                DESKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        //判断返回的结果
        int r = res.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //返回订单id
        return Result.ok(orderId);
    }


    /**
     * 创建优惠卷订单
     *  此处不加锁应该也行，毕竟 lua脚本已经做了判断拦截，不过也要保证万无一失
     * @param voucherOrder
     * @return
     */
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean b = lock.tryLock();
        if (!b){
            log.error("不允许重复下单！");
            return;
        }

        try {
            //取数据库中查询用户是否已经创建过订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0){
                //用户已经购买过了
                log.error("不允许重复下单！");
                return;
            }
            //扣减库存
            boolean update = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId).gt("stock", 0)
                    .update();
            if (!update){
                log.error("库存不足");
                return;
            }
            // 7.创建订单 还有其它信息也可以通过消息队列传递。。。。todo
            voucherOrder.setCreateTime(LocalDateTime.now());
            voucherOrder.setUpdateTime(LocalDateTime.now());
            save(voucherOrder);
        }finally {
            lock.unlock();
        }
    }

    /**
     * 监听消息队列
     */
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //1.获取消息队列的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    //lastConsumed 相当于 > 表示下一个未消费的消息
                    List<MapRecord<String, Object, Object>> mapRecords = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    //2.判断订单信息是否为空
                    if (mapRecords == null || mapRecords.isEmpty()) {
                        //如果为空就继续监听，进入下一次循环
                        continue;
                    }
                    //因为只获取一个消息，所以集合中只有一个元素
                    parseData(mapRecords);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        /**
         * 处理 pending-list中的消息，（当消息被消费但没确认就会进入到 pending-list中）
         */
        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有异常消息，结束循环
                        break;
                    }
                    // 解析数据
                    parseData(list);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }

        /**
         * 解析处理消息数据
         * @param list
         */
        private void parseData(List<MapRecord<String, Object, Object>> list) {
            MapRecord<String, Object, Object> record = list.get(0);
            Map<Object, Object> value = record.getValue();
            VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
            // 3.创建订单
            createVoucherOrder(voucherOrder);
            // 4.确认消息 XACK
            stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
        }
    }
}

package com.qzdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qzdp.dto.Result;
import com.qzdp.entity.Voucher;
import com.qzdp.mapper.VoucherMapper;
import com.qzdp.entity.SeckillVoucher;
import com.qzdp.service.ISeckillVoucherService;
import com.qzdp.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.ZoneOffset;
import java.util.List;

import static com.qzdp.utils.RedisConstants.SECKILL_STOCK_KEY;
import static com.qzdp.utils.RedisConstants.SECKILL_TIME_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *

 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        // 保存秒杀卷信息到Redis中
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(),
                voucher.getStock().toString());
        stringRedisTemplate.opsForValue().set(SECKILL_TIME_KEY + voucher.getId(),
                voucher.getBeginTime().toEpochSecond(ZoneOffset.UTC) + "-" + voucher.getEndTime().toEpochSecond(ZoneOffset.UTC));
    }
}


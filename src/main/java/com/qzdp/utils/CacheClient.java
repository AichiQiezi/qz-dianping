package com.qzdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.qzdp.utils.RedisConstants.*;

/**
 * @author haofeng
 * @date 2022/10/13 20:46
 * @description 缓存客户端，封装一些对 redis的操作
 */

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate redisTemplate) {
        this.stringRedisTemplate = redisTemplate;
    }

    /**
     * 定义线城池
     */
    @Autowired
    private ThreadPoolExecutor pool;

    /*
     * * 方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     * * 方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
     *
     * * 方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     * * 方法4：根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
     */

    /**
     * 任意Java对象序列化为json并存储在string类型的key中,并且可以设置TTL过期时间
     *
     * @param key   不为空
     * @param value 不为空
     * @param time  过期时间数值
     * @param unit  过期时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }



    /**
     * 将任意Java对象序列化为 json并存储在 string类型的 key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
     *
     * @param key   不为空
     * @param value 不为空
     * @param time  过期时间数值
     * @param unit  过期时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入 redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 利用逻辑过期解决缓存击穿问题
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)){
        //  缓存未命中
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())){
            //没有过期，直接返回商铺信息
            return r;
        }
        //过期了就交给一个新的线程去异步更新缓存
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //获取互斥锁
        if (isLock){
            //开启独立线程，实现缓存重建
            pool.submit(()->{
                try {
                    R apply = dbFallback.apply(id);
                    if (apply != null){
                        //数据库存在,实现缓存重建
                        this.setWithLogicalExpire(key, apply, time, unit);
                    }
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        //逻辑过期方式就不需要cas重试了
        //直接返回过期的商铺信息
        return r;
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     *
     * @param keyPrefix
     * @param id         商铺 id
     * @param type       转化为的类型
     * @param dbFallback 函数接口，一个参数和一个返回值
     * @param time
     * @param unit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String redisValue = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(redisValue)) {
            //缓存中存在，直接返回结果
            return JSONUtil.toBean(redisValue, type);
        }
        // 判断命中的是否是空值
        if (redisValue != null) {
            // 返回一个错误信息
            return null;
        }
        //如果为空，就去数据库查询，查不到就缓存一个空值并返回
        //查询数据库
        R apply = dbFallback.apply(id);
        //数据中不存在
        if (apply == null) {
            //将空值写入 redis中去
            this.set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //存在，则保存到redis中去
        this.set(key, JSONUtil.toJsonStr(apply), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return apply;
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     *  带有互斥锁
     * @param keyPrefix
     * @param id         商铺 id
     * @param type       转化为的类型
     * @param dbFallback 函数接口，一个参数和一个返回值
     * @param time
     * @param unit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(shopJson, type);
        }
        // 判断命中的是否是空值，那什么时候为null呢
        //如果shopJson不为空，根据上面的StrUtil.isNotBlank来判断，shopJson一定为空值
        if (shopJson != null) {
            // 返回一个错误信息
            log.debug("shopJson:{}",shopJson);
            return null;
        }

        // 4.实现缓存重建
        // 4.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2.判断是否获取成功
            if (!isLock) {
                // 4.3.获取锁失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            // 4.4.获取锁成功，首先判断缓存是否存在了，不存在才去根据id查询
            // 4.5 再判断缓存中是否已经存在了，dck（双重检查的思想）
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)){
                //缓存中已经存在，直接返回即可
                return JSONUtil.toBean(shopJson,type);
            }
            r = dbFallback.apply(id);
            // 5.不存在，返回错误
            if (r == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 6.存在，写入redis
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 7.释放锁
            unlock(lockKey);
        }
        // 8.返回
        return r;
    }


    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //防止自动拆箱出现错误,flg可能为 null
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

}

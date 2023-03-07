package com.hmdp.utils;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


@Slf4j
@Component
public class CacheClint {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //构建缓存
    public void set(String key, Object value, Long timeout, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),timeout,unit);
    }
    //设置逻辑过期
    public void setLogicExpire(String key,Object value,Long timeout,TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(timeout)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }
    //查询缓存，并使用空值缓存的方法避免缓存穿透
    public <R,ID> R get(Class<R> type,String IDPrefix ,ID id, Function<ID,R> dbFallBack,Long timeout, TimeUnit unit){
        String key = IDPrefix + id;
        String cache = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(cache)){
            return JSONUtil.toBean(cache,type);
        }
        if (Objects.equals(cache, "")){
            return null;
        }
        R apply = dbFallBack.apply(id);
        if (apply==null){
            set(key,"",timeout,unit);
            return null;
        }
        set(key,apply,timeout,unit);
        return apply;
    }
    //逻辑更新解决缓存击穿
    public <R,ID> R queryLogicalExpiration(String IDPrefix,String lockIdPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,
                                           Long expireSecond,TimeUnit unit){
        String key = IDPrefix+ id;
        //从redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue().get(key);
        //缓存未命中，逻辑更新意味着预热的数据理论上永远存在于缓存中，这里查不到意味着没有预热
        if (StrUtil.isBlank(shopJSON)){
            return null;
        }
        //命中后查询是否过期
        RedisData redisData = JSONUtil.toBean(shopJSON, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())){
            //未过期
            return r;
        }
        //过期->获取锁
        String lockId = lockIdPrefix + id;
        Boolean aBoolean = tryLock(lockId);
        if (aBoolean) {
            //成功获取锁
            CACHE_REBUILD_EXECUTOR.submit(()->{
                //重建缓存
                try {
                    R shopDate = dbFallBack.apply(id);
                    this.setLogicExpire(key,shopDate,expireSecond,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    releaseLock(lockId);
                }
            });
        }
        return r;
    }
    //实现了互斥锁防止缓存击穿，空值缓存防止缓存穿透
    public <R,ID> R queryMutex(String IDPrefix,String lockIdPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,
                             Long timeout,TimeUnit unit) {
        String key = IDPrefix+ id;
        //从redis中查询商铺缓存
        String cacheJSON = stringRedisTemplate.opsForValue().get(key);
        //若存在，直接返回
        if (StrUtil.isNotBlank(cacheJSON)) {
            return JSONUtil.toBean(cacheJSON,type);
        }
        //若缓存value为空的键值对，直接返回
        if (Objects.equals(cacheJSON, "")){
            return null;
        }
        //未命中redis缓存
        R r=null;
        //尝试获取锁
        String keyLock = lockIdPrefix + id;
        try {
            if (!tryLock(keyLock)) {
                //未获取锁
                Thread.sleep(50);
                return queryMutex(IDPrefix,lockIdPrefix,id,type,dbFallBack,timeout,unit);
            }
            //获取到锁，进行重建
            r = dbFallBack.apply(id);
            if (r==null){
                stringRedisTemplate.opsForValue().set(key,"",2L,unit);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(r),timeout, unit);
        }catch (InterruptedException exception){
            throw new RuntimeException(exception);
        }finally {//释放锁
            releaseLock(keyLock);
        }
        return r;
    }
    public Boolean tryLock(String key){
        return stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    }
    public void releaseLock(String key){
        stringRedisTemplate.delete(key);
    }
}

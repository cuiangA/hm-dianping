package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    //使用用户名作为lockId的后半部分
    private String userId;
    private final String KEY_PREFIX="lock:";
    private final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";
    private StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> UNLOCK_LUA;
    static {
        UNLOCK_LUA = new DefaultRedisScript<>();
        ClassPathResource classPathResource = new ClassPathResource("unLock.lua");
        UNLOCK_LUA.setLocation(new ClassPathResource("unLock.lua"));
    }

    public SimpleRedisLock(String userId, StringRedisTemplate stringRedisTemplate) {
        this.userId = userId;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        //把线程id作为锁的值来避免误删问题
        String ThreadId = ID_PREFIX+Thread.currentThread().getId()+"";
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + userId
                , ThreadId, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(result);
    }
    @Override
    public void delLock() {
        //采用lua脚本来保证查询锁的id与当前线程id是否一致，和删除锁的原子性
            stringRedisTemplate.execute(
                    UNLOCK_LUA,
                    Collections.singletonList(KEY_PREFIX + userId),
                    ID_PREFIX+Thread.currentThread().getId()+""
            );
        }
    }
//    @Override
//    public void delLock() {
//        //获得自己线程的id
//        String ThreadId = ID_PREFIX+Thread.currentThread().getId()+"";
//        //获得取得锁的的值也就是当前获得锁的线程的id
//        String lockValue = stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
//        //比较两个id，查看此锁是否为该线程的
//        if (ThreadId.equals(lockValue)) {
//            stringRedisTemplate.delete(KEY_PREFIX + userId);
//        }


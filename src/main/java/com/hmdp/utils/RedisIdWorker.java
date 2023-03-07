package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
/*
生成全局id
 */
@Component
public class RedisIdWorker {
    private final Long BEGIN_TIMESTAMP = 1677456000L;
    private final int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

//    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    public Long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        //生成序列号
        //获取当前时期精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        return timeStamp << COUNT_BITS | increment;
    }
}

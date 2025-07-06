package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
/**
 * 全局id生成器
 * 生成的是64位id
 */
public class RedisIdWorker {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private final long BEGIN_TIMESTAMP = 1640995200L;
    //32位的序列号
    private final int COUNT_BITS = 32;


    public long nextId(String keyPrefix) {
        // 获取当前时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(java.time.ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //使用redis的自增长
        long count = stringRedisTemplate.opsForValue().increment("incr:" + keyPrefix + ":" + date);

        // 返回ID的长整型值
        return timestamp << COUNT_BITS | count;

    }


}

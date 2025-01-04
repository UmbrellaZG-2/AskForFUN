package com.AFF.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDworker {
    private static final long TIMESTAMP_BASE=32L;
    private StringRedisTemplate stringRedisTemplate;
    private static  final long BEGIN_TIMESTAMP = 1704067200L;
    public RedisIDworker(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }
    public long nextID(String keyprefix){
        //生产时间戳
        LocalDateTime now=LocalDateTime.now();
        long nowSecond=now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp=nowSecond-BEGIN_TIMESTAMP;
        //生成序列号
        String dataKey=now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count=stringRedisTemplate.opsForValue().increment("icr"+keyprefix+":"+dataKey);
        //拼接并返回
        return timeStamp<<TIMESTAMP_BASE|count;
    }
}

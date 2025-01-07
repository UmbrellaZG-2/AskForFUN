package com.AFF.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class RedisLock implements ILock{
    private StringRedisTemplate stringRedisTemplate;
    private String name;

    private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";
    private static final String KEY_PREFIX = "lock:";
    private static final DefaultRedisScript UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public RedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        String key = KEY_PREFIX + name;
        //获取线程标识
        long ThreadId = Thread.currentThread().getId();
        String ThreadIdStr = ID_PREFIX + ThreadId;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, ThreadIdStr, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
//        //获取线程标识
//        long ThreadId = Thread.currentThread().getId();
//        String ThreadIdStr = ID_PREFIX + ThreadId;
//        //获取锁中表示
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //id一致就能删除
//        if (ThreadIdStr.equals(id)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
        //调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }
}

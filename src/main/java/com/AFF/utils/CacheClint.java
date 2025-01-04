package com.AFF.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.AFF.entity.Shop;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.AFF.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClint {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClint(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value,Long time, TimeUnit unit) {
        this.stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time, unit);
    }
    public void setWithLogicalExpire(String key, Object value,Long time, TimeUnit unit) {
        RedisData redisData=new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        this.stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                         Function<ID,R> dbFallBack, Long time, TimeUnit unit){
        //查询redis缓存.
        String key=keyPrefix+id;
        String Json = this.stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        //存在则返回
        if (StringUtils.isNotBlank(Json)){
            return JSONUtil.toBean(Json, type);
        }
        //命中空值，则返回
        if (Json!=null){
            return null;
        }
        //不存在则查id数据库
        R r =dbFallBack.apply(id);
        //数据库也不在则返回错误
        if(r==null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",
                    CACHE_NULL_TTL+(long) (Math.random() * 11) - 5, TimeUnit.MINUTES);
            return null;
        }
        //存在，写入redis
        this.set(key,r,time,unit);
        //返回
        return r;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    public <R,ID> R queryWithLogicalExpire  (String keyPrefix,ID id,
                                          Class<R> type,Function<ID,R> dbFallback,
                                             Long time, TimeUnit unit) {
        String key=keyPrefix+id;
        String json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //判断是否存在
        //存在则返回
        if (StringUtils.isNotBlank(json)) {
            return null;
        }
        //需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断过期没
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，返回
            return r;
        }
        //过期则缓存重建
        //获取互斥锁
        String loclKey = LOCK_SHOP_KEY + id;
        //判断锁获取成功没
        boolean isLock = tryLock(loclKey);
        //成功，开启独立线程，实现重建
        if (isLock) {
            try{
                R r1=dbFallback.apply(id);
                this.setWithLogicalExpire(key,r1,time,unit);
            }catch (Exception e){
                throw new RuntimeException();
            }finally {
                unLock(loclKey);
            }
        }
        //返回
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}

package com.AFF.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.AFF.dto.Result;
import com.AFF.entity.Shop;
import com.AFF.mapper.ShopMapper;
import com.AFF.service.IShopService;
import com.AFF.utils.CacheClint;
import com.AFF.utils.RedisData;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.AFF.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author UMBRELLAZG
 * @since 2024-11-21
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringredisTemplate;
    @Resource
    private CacheClint cacheClint;
    @Override
    public Result queryShopById(Long id){
        //空值解决缓存穿透
//        Shop shop=queryWithPassThrough(id);
        //互斥锁解决缓存击穿
//        Shop shop=queryWithMutex(id);
//        工具类解决缓存击穿
        Shop shop=cacheClint.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,
                this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        //逻辑过期解决缓存击穿
//        Shop shop=queryWithLogicalExpire(id);
        //使用工具类解决
//        Shop shop=cacheClint.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,
//                this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if (shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

//    public Shop queryWithPassThrough(long id){
//        //查询redis缓存
//        String shopJson = stringredisTemplate.opsForValue().get(CACHE_SHOP_KEY+ id);
//        //判断是否存在
//        //存在则返回
//        if (StringUtils.isNotBlank(shopJson)){
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //命中空值，则返回
//        if (shopJson!=null){
//            return null;
//        }
//        //不存在则查id数据库
//        Shop shop = getById(id);
//        //数据库也不在则返回错误
//        if(shop==null){
//            //将空值写入redis
//            stringredisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",
//                    CACHE_NULL_TTL+(long) (Math.random() * 11) - 5, TimeUnit.MINUTES);
//            return null;
//        }
//        //存在，写入redis
//        stringredisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),
//                CACHE_SHOP_TTL+(long) (Math.random() * 11) - 5, TimeUnit.MINUTES);
//        //返回
//        return shop;
//    }

    public Shop queryWithMutex(long id) {
        //查询redis缓存
        String shopJson = stringredisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //判断是否存在
        //存在则返回
        if (StringUtils.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //命中空值，则返回
        if (shopJson != null) {
            return null;
        }
        //不存在实现缓存重建
        //获取互斥锁
        String loclKey = LOCK_SHOP_KEY + id;
        Shop shop=null;
        //获取失败则休眠并重试
        try {
            boolean flag = tryLock(loclKey);
            if (!flag) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //获取锁成功
            //根据id查询数据库
            shop = getById(id);
            //不存在，返回空值，并将空值写入redis
            if (shop == null) {
                stringredisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "",
                        CACHE_NULL_TTL + (long) (Math.random() * 11) - 5, TimeUnit.MINUTES);
                return null;
            }
            //存在，写入redis
            stringredisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                    CACHE_SHOP_TTL + (long) (Math.random() * 11) - 5, TimeUnit.MINUTES);
            //释放互斥锁
            //返回
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(loclKey);
        }
        return shop;
    }
//    //线程池
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
//    public Shop queryWithLogicalExpire  (long id) {
//        //查询redis缓存
//        String shopJson = stringredisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        //判断是否存在
//        //存在则返回
//        if (StringUtils.isNotBlank(shopJson)) {
//            return null;
//        }
//        //需要先把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject)redisData.getData();
//        Shop shop=JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //判断过期没
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            //未过期，返回
//            return JSONUtil.toBean(data, Shop.class);
//        }
//        //过期则缓存重建
//        //获取互斥锁
//        String loclKey = LOCK_SHOP_KEY + id;
//        //判断锁获取成功没
//        boolean isLock = tryLock(loclKey);
//        //成功，开启独立线程，实现重建
//        if (isLock) {
//            try{
//                CACHE_REBUILD_EXECUTOR.submit(() -> {
//                    this.saveShop2Redis(id,20L);
//                });
//            }catch (Exception e){
//                throw new RuntimeException();
//            }finally {
//                unLock(loclKey);
//            }
//        }
//        //返回
//        return shop;
//    }

    public void saveShop2Redis(Long id,Long expireSeconds){
        Shop shop=getById(id);
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringredisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id==null){
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringredisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }


    private boolean tryLock(String key){
        Boolean flag = stringredisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringredisTemplate.delete(key);
    }
}

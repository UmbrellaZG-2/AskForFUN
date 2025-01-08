package com.AFF.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.AFF.dto.Result;
import com.AFF.entity.Shop;
import com.AFF.mapper.ShopMapper;
import com.AFF.service.IShopService;
import com.AFF.utils.CacheClint;
import com.AFF.utils.RedisData;
import com.AFF.utils.SystemConstants;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.nio.sctp.InvalidStreamException;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要根据坐标查询
        if (x == null||y == null){
            //不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            //返回数据
            return Result.ok(page.getRecords());
        }
        //计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //查询Redis，按照距离排序，分页，结果：shopId distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringredisTemplate.opsForGeo().search(key, GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        // 解析出id
        if(results==null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        List<Long> ids = new ArrayList<>(content.size());
        Map<String,Distance> distanceMap = new HashMap<>(content.size());
        content.stream().skip(from).forEach(result->{
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance= result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });
        //根据id查询shop
        String idStr=StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for(Shop shop:shops){
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //返回
        return Result.ok(shops);
    }


    private boolean tryLock(String key){
        Boolean flag = stringredisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringredisTemplate.delete(key);
    }
}

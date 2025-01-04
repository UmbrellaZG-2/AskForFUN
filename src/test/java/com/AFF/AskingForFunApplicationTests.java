package com.AFF;

import com.AFF.entity.Shop;
import com.AFF.service.impl.ShopServiceImpl;
import com.AFF.utils.CacheClint;
import com.AFF.utils.RedisIDworker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.AFF.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class  AskingForFunApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClint cacheClint;
    @Resource
    private RedisIDworker redisIDworker;

//    private ExecutorService es=  Executors.newFixedThreadPool(500);
//    @Test
//    void testSaveShop(){
//        Shop shop=shopService.getById(1L);
//        cacheClint.setWithLogicalExpire(CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.SECONDS);
//    }
//    @Test
//    void testIdWorker(){
//        CountDownLatch latch=new CountDownLatch(300);
//        Runnable task = ()->{
//            for (int i = 0; i < 3; i++) {
//                long id = redisIDworker.nextID("order");
//                System.out.println("id = " + id);
//            }
//            latch.countDown();
//        };
//        long begin=System.currentTimeMillis();
//        for (int i = 0; i < 10; i++){
//            es.submit(task);
//        }
//        try {
//            latch.await();
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        long end=System.currentTimeMillis();
//        System.out.println("time = " + (end-begin));
//    }

}

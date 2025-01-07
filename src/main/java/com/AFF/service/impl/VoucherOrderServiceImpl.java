package com.AFF.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.AFF.dto.Result;
import com.AFF.entity.VoucherOrder;
import com.AFF.mapper.VoucherOrderMapper;
import com.AFF.service.ISeckillVoucherService;
import com.AFF.service.IVoucherOrderService;
import com.AFF.utils.RedisIDworker;
import com.AFF.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author UMBRELLAZG
 * @since 2024-11-21
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIDworker redisIDworker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("Seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();
    private IVoucherOrderService proxy;

    //通过阻塞队列实现异步
//    private BlockingQueue<VoucherOrder> ordertasks=new ArrayBlockingQueue<>(1024*1024);
//    @PostConstruct
//    private void init(){
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler()) ;
//    }
//
//    private class VoucherOrderHandler implements Runnable{
//
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    VoucherOrder voucherOrder = ordertasks.take();
//                    handleVoucherOrder(voucherOrder);
//                } catch (InterruptedException e) {
//                    log.error("处理订单异常");
//                }
//            }
//        }
//    }

        @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler()) ;
    }

    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            String queueName="stream.orders";
            while (true) {
                try {
                    //获取消息队列中的消息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //判断消息是否为空
                    if (list == null || list.isEmpty()) {
                        //如果为空，说明没有消息，继续下一次循环
                        continue;
                    }
                    //获取成功就可以下单
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.mapToBean(values, VoucherOrder.class, false);
                    handleVoucherOrder(voucherOrder);
                    //做XACK消息确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    //如果出现异常，说明消息出错了，可以进行重试或者跳过
                    log.error("处理订单异常",e);
                    handlePendingMessages(queueName);
                }
            }
        }

        private void handlePendingMessages(String queueName) {
            while (true) {
                try {
                    //获取消息队列中的消息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //判断消息是否为空
                    if (list == null || list.isEmpty()) {
                        //如果为空，说明没有消息，继续下一次循环
                        break;
                    }
                    //获取成功就可以下单
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.mapToBean(values, VoucherOrder.class, false);
                    handleVoucherOrder(voucherOrder);
                    //做XACK消息确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    //如果出现异常，说明消息出错了，可以进行重试或者跳过
                    log.error("处理阻塞队列中订单异常", e);
                }
            }
        }
    }
    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户
        Long userID=voucherOrder.getUserId();
         //创建锁对象
        RLock lock=redissonClient.getLock("lock:order:"+userID);
        //尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            //获取锁失败，直接返回失败或者重试
            log.error("非法请求，不允许重复下单");
        }
        //获取代理对象
        try{
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            lock.unlock();
        }

    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userID=UserHolder.getUser().getId();
        //获取订单id
        Long orderID=redisIDworker.nextID("order");
        //获取
        //执行lua脚本，得到资格
        Long result=stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userID.toString(),
                String.valueOf(orderID)
        );
        //判断资格是否为0
        int r=result.intValue();
        //不为0则没有购买资格
        if (r!=0) {
            return Result.fail(r==1?"库存不足":"该用户不能重复下单");
        }
        proxy = (IVoucherOrderService)AopContext.currentProxy();

        return Result.ok(orderID);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //获取用户id
//        Long userID=UserHolder.getUser().getId();
//        //执行lua脚本，得到资格
//        Long result=stringRedisTemplate.execute(SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),
//                userID.toString()
//        );
//        //判断资格是否为0
//        int r=result.intValue();
//        //不为0则没有购买资格
//        if (r!=0) {
//            return Result.fail(r==1?"库存不足":"该用户不能重复下单");
//        }
//        //为0则把下单信息保存到阻塞队列
//        //返回订单id
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //订单id
//        long orderId = redisIDworker.nextID("order");
//        voucherOrder.setId(orderId);
//        //用户id
//        voucherOrder.setUserId(userID);
//        voucherOrder.setVoucherId(voucherId);
//        ordertasks.add(voucherOrder);
//        //获取代理对象
//        IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//
//        return Result.ok(orderId);
//    }
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //尚未开始
//            return Result.fail("秒杀尚未开始");
//        }
//        //判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.of(2025,3,1,0,0,0))) {
//            //已经结束
//            return Result.fail("秒杀已经结束");
//        }
//        //判断库存充足吗
//        if (voucher.getStock() < 1) {
//            //库存不足
//            return Result.fail("库存不足");
//        }
//        Long userID=UserHolder.getUser().getId();
//        synchronized(userID.toString().intern()){
//        //创建锁对象
//        ILock lock = new RedisLock(stringRedisTemplate,"order:"+userID);
//        RLock lock = redissonClient.getLock("lock:order:"+userID);
//        //尝试获取锁
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            //获取锁失败，直接返回失败或者重试
//            return Result.fail("非法请求，不允许重复下单");
//        }
//        //获取代理对象
//        try{
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            lock.unlock();
//        }
//    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //一人一单
        //根据优惠卷id和用户id查询订单
        Long userID=voucherOrder.getUserId();
        Long cnt=query().eq("voucher_id", voucherOrder).eq("user_id", userID).count();
        //判断存在，如果已有则不能
        if (cnt>0) {
            log.error("订单重复");
            return;
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder)
                .gt("stock", 0)
                .update();
        if (!success) {
            //扣减失败
            log.error("库存不足");
            return;
        }

        save(voucherOrder);
    }
}

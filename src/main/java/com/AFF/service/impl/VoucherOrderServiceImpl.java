package com.AFF.service.impl;

import com.AFF.dto.Result;
import com.AFF.entity.SeckillVoucher;
import com.AFF.entity.User;
import com.AFF.utils.ILock;
import com.AFF.entity.VoucherOrder;
import com.AFF.mapper.VoucherOrderMapper;
import com.AFF.service.ISeckillVoucherService;
import com.AFF.service.IVoucherOrderService;
import com.AFF.utils.RedisIDworker;
import com.AFF.utils.RedisLock;
import com.AFF.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("秒杀尚未开始");
        }
        //判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.of(2025,3,1,0,0,0))) {
            //已经结束
            return Result.fail("秒杀已经结束");
        }
        //判断库存充足吗
        if (voucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足");
        }
        Long userID=UserHolder.getUser().getId();
//        synchronized(userID.toString().intern()){
        //创建锁对象
        ILock lock = new RedisLock(stringRedisTemplate,"order:"+userID);
        //尝试获取锁
        boolean isLock = lock.tryLock(1200);
        if (!isLock) {
            //获取锁失败，直接返回失败或者重试
            return Result.fail("非法请求，不允许重复下单");
        }
        //获取代理对象
        try{
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            lock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //一人一单
        //根据优惠卷id和用户id查询订单
        Long userID=UserHolder.getUser().getId();
        Long cnt=query().eq("voucher_id", voucherId).eq("user_id", userID).count();
        //判断存在，如果已有则不能
        if (cnt>0) {
            return Result.fail("该用户不能重复下单");
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            //扣减失败
            return Result.fail("库存不足");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIDworker.nextID("order");
        voucherOrder.setId(orderId);
        //用户id
        Long user = UserHolder.getUser().getId();
        voucherOrder.setUserId(user);
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}

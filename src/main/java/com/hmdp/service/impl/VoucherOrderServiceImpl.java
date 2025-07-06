package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    RedisIdWorker redisIdWorker;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedissonClient redissonClient;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {

        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }

        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("活动已结束");
        }

        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
//        //intern()方法可以将字符串常量池中的字符串对象进行去重，保证锁的是当前用户
//        synchronized (userId.toString().intern()) {
//            //获取当前代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            //调用代理对象的方法
////            return createVoucherOrder(voucherId);   这里实际上是用this调用，会带来事务失效的问题
//            // 这里使用代理对象调用方法是为了确保事务能够正常工作
//            return proxy.createVoucherOrder(voucherId);
//        }

        //使用分布式锁，防止一个用户重复下单
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);

        RLock lock = redissonClient.getLock("lock:order" + userId);

//        boolean isLock = lock.tryLock(10L);
        boolean isLock = lock.tryLock();

        //判断获取锁是否成功
        if(!isLock){
            return Result.fail("一个人不允许重复下单");
        }
        try {
            //获取当前代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //调用代理对象的方法
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //判断用户是否已经购买过，一人只能买一单哦
        Long userId = UserHolder.getUser().getId();
        int count = this.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已购买过");
        }
//        扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId)
//                .eq("stock", voucher.getStock())//乐观锁
                //上面的乐观锁在高并发情况下可能会导致问题，因为多个线程可能会同时读取到相同的库存值。
                .gt("stock", 0) //乐观锁
                .update();
        if (!success) {
            return Result.fail("创建订单失败");
        }

        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
//        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);

//        订单写入数据库
        boolean saveSuccess = this.save(voucherOrder);
        if (!saveSuccess) {
            // 回滚库存
            seckillVoucherService.update()
                    .setSql("stock = stock + 1")
                    .eq("voucher_id", voucherId)
                    .update();
            return Result.fail("创建订单失败");
        }
        return Result.ok(orderId);
    }
}

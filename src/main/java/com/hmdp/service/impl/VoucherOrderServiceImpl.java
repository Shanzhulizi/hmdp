package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
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

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//
//
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀未开始");
//        }
//
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("活动已结束");
//        }
//
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//
//
//        Long userId = UserHolder.getUser().getId();
////        //intern()方法可以将字符串常量池中的字符串对象进行去重，保证锁的是当前用户
////        synchronized (userId.toString().intern()) {
////            //获取当前代理对象
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            //调用代理对象的方法
//////            return createVoucherOrder(voucherId);   这里实际上是用this调用，会带来事务失效的问题
////            // 这里使用代理对象调用方法是为了确保事务能够正常工作
////            return proxy.createVoucherOrder(voucherId);
////        }
//
//        //使用分布式锁，防止一个用户重复下单
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//
//        RLock lock = redissonClient.getLock("lock:order" + userId);
//
////        boolean isLock = lock.tryLock(10L);
//        boolean isLock = lock.tryLock();
//
//        //判断获取锁是否成功
//        if (!isLock) {
//            return Result.fail("一个人不允许重复下单");
//        }
//        try {
//            //获取当前代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            //调用代理对象的方法
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//
//    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    //阻塞队列
    private BlockingQueue<VoucherOrder> orderTasksQueue = new ArrayBlockingQueue<>(1024 * 1024);
    //线程池，使用子线程处理订单信息
    private static final ExecutorService ORDER_TASKS_EXECUTOR = Executors.newSingleThreadExecutor();

    //线程初始化
    @PostConstruct//初始化完毕就立即执行
    private void init() {
        //启动线程
        ORDER_TASKS_EXECUTOR.submit(new VoucherOrderHandler());
    }

    //线程任务
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    //获取订单信息
                    VoucherOrder voucherOrder = orderTasksQueue.take();
                    //创建订单
                    createVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.info("订单处理线程被中断，退出循环", e);
                    Thread.currentThread().interrupt();
                    break; // 退出循环
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order" + userId);

        boolean isLock = lock.tryLock();

        //判断获取锁是否成功
        if (!isLock) {
            log.error("一个人不允许重复下单");
            return;
        }
        try {
            //调用代理对象的方法
          proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }

    }

    private IVoucherOrderService proxy;

    /**
     * 使用Lua脚本取代判断逻辑
     * 有资格的直接放入队列，加锁下单的逻辑交给另一个线程
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {

        Long userId = UserHolder.getUser().getId();
        //执行脚本
        Long execute = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), // 传入的参数：代金券ID
                userId.toString() // 传入的参数：用户ID
        );

//        判断结果不为0，没有购买资格
        if (execute.intValue() != 0) {
            return Result.fail(execute.intValue() == 1 ? "库存不足" : "用户已购买过");
        }

        Long orderId = redisIdWorker.nextId("order");
        //把下单信息保存到消息队列
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        orderTasksQueue.add(voucherOrder);

        //获取当前代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();


        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //判断用户是否已经购买过，一人只能买一单哦
        Long userId = voucherOrder.getId();
        int count = this.query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("用户已经购买过该代金券");
            return;
        }
//        扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherOrder.getVoucherId())
//                .eq("stock", voucher.getStock())//乐观锁
                //上面的乐观锁在高并发情况下可能会导致问题，因为多个线程可能会同时读取到相同的库存值。
                .gt("stock", 0) //乐观锁
                .update();
        if (!success) {
            log.error("扣减库存失败，可能库存不足");
            return;
        }


//        订单写入数据库
        save(voucherOrder);


    }
}

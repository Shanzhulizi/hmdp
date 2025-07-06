package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Test
    void testSaveShopToRedis() {
        shopService.saveShopToRedis(1L, 10L);
    }

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() {
        Runnable task = () -> {
            try {
                for (int i = 0; i < 100; i++) {
                    long id = redisIdWorker.nextId("order");
                    // 打印时强制刷新缓冲区
                    System.out.println("id = " + id);
                    System.out.flush(); // 立即刷新输出
                }
            } catch (Exception e) {
                e.printStackTrace(); // 捕获异常，避免子线程静默失败
                System.err.flush();
            }
        };

        // 提交任务
        for (int i = 0; i < 100; i++) {
            es.submit(task);
        }

        // 关键：等待所有子线程执行完毕
        es.shutdown();
        // 等待最多10分钟（根据任务耗时调整）
        boolean isTerminated = false;
        try {
            isTerminated = es.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (!isTerminated) {
            es.shutdownNow(); // 超时未完成则强制终止
        }

        // 手动刷新所有输出流
        System.out.flush();
        System.err.flush();
    }

}

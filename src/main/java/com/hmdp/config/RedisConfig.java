package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 这是对Redisson的配置类
 * */
@Configuration
public class RedisConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");

        return Redisson.create(config);
    }

    /**
     * 联锁 Multi-Lock
     * 这里是多个Redis来解决主从同步问题，都是主就没有主从的问题了
     * @return
     */
    //不行，6379只能让一个占用，反正你大概知道是怎么回事就好
//    @Bean
//    public RedissonClient redissonClient2() {
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://127.0.0.1:6380").setPassword("");
//
//        return Redisson.create(config);
//    }
//    @Bean
//    public RedissonClient redissonClient3() {
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("");
//
//        return Redisson.create(config);
//    }
}

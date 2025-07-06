package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {


    private String name;
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";

    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-"; //使用UUID作为锁的标识前缀，确保唯一性

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();//作为锁的标识

        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        //拆箱有null的可能
        return Boolean.TRUE.equals(success);
    }
//
//    @Override
//    public void unlock() {
//        //获取当前线程的锁标识
//        String  threadId = ID_PREFIX + Thread.currentThread().getId();//作为锁的标识
//
//        //获取锁的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//
//        if(threadId.equals(id)) {
//            //如果当前线程的锁标识和存储的锁标识一致，说明是当前线程持有锁，可以解锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//
//        }
//
//    }
//

    /**
     * 改用Lua脚本保证原子操作
     */
    @Override
    public void unlock() {
        //调用Lua脚本进行解锁操作

        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());

    }
}




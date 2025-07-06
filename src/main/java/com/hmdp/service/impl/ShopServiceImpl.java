package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    IShopService shopService;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = queryWithPassThrough(id);

        //互斥锁，防止缓存击穿
//        Shop shop = queryWithMutex(id);

        Shop shop = queryWithLogicalExpires(id);
        if (shop == null) {
            //如果没有，返回错误
            return Result.fail("商铺不存在");
        }

        return Result.ok(shop);
    }

    /**
     * 这里是为了防止缓存穿透
     *
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        //        从redis中查询商铺信息
        //这里Hash也行，用value只是为了各种都用一用
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);

        }
        if (shopJson != null) {
            //上面的逻辑判断了是否为空
            //这里判断是不是null,如果不是null，说明是防止穿透的空串
            return null;
        }

        //如果没有，查询数据库
        Shop shop = shopService.getById(id);
        if (shop == null) {
            //如果数据库也没有
            //空值写入redis，防止缓存击穿
            stringRedisTemplate.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return null;
        }

        //如果数据库有，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }


    public Shop queryWithMutex(Long id) {


        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null) {
            //上面的逻辑判断了是否为空
            //这里判断是不是null,如果不是null，说明是防止穿透的空串
            return null;
        }

        //如果没有，查询数据库
//        为防止缓存击穿，使用互斥锁
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            //获取锁
            boolean isLock = tryLock(lockKey);
            //是否获取成功

            if (!isLock) {
                //失败则休眠重试
                Thread.sleep(200);
                queryWithMutex(id);
            }

            //获取锁成功

            shop = shopService.getById(id);
            if (shop == null) {
                //如果数据库也没有
                //空值写入redis，防止缓存击穿
                stringRedisTemplate.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return null;
            }

            //如果数据库有，写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey); // 释放锁
        }


        return shop;


    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期防止缓存击穿
     * 这里存的数据就是RedisData对象，而不是shop对象
     *
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpires(Long id) {


        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        //如果缓存命中，先反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);

        LocalDateTime expireTime = redisData.getExpireTime();
        Shop shop = (Shop) redisData.getData();
        //判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //如果没有过期，直接返回
            return shop;
        }

        //如果过期，缓存重建
        //获取互斥锁
        //开启独立线程重建缓存
        String lockKey = LOCK_SHOP_KEY + id;

        boolean isLock = tryLock(lockKey);

        if (isLock) {
            //如果获取锁成功，那就开启独立线程，实现缓存重建

            //这里使用线程池来执行缓存重建任务
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShopToRedis(id, CACHE_SHOP_TTL);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });

        }

        //如果获取锁失败，直接返回旧数据
        return shop;

    }



    private boolean tryLock(String key) {
        // 尝试获取锁
        // setIfAbsent 方法会在 key 不存在时设置值，并返回 true；如果 key 已存在，则返回 false
        // setIfAbsent就是 setnx
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    private void unlock(String key) {
        // 删除锁
        stringRedisTemplate.delete(key);
    }

    /**
     * 预热，把热点数据提前放入缓存的代码
     * 这个要放到后台管理的，但是这个项目没有后台管理
     *
     * @param id
     * @param expireSeconds
     */
    public void saveShopToRedis(Long id, Long expireSeconds) {
        //查询商铺信息
        Shop shop = getById(id);
        if (shop == null) {
            return;
        }
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        //设置过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));//逻辑过期不要设置过期时间
    }


    /**
     * 更新商铺信息
     * 这里是服务端调用的
     *
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }

//        1.更新数据库
        updateById(shop);

//        2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}

package com.hmdp.controller;


import ch.qos.logback.classic.spi.EventArgUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.setting.SettingUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CATEGORY_KEY;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        //对查询分类添加缓存，查询结果是一个List，我们是用Redis里的Set来存

        // 1. 先查缓存
        String json = stringRedisTemplate.opsForValue().get(CATEGORY_KEY);
        if (StringUtils.hasText(json)) {
            // 2. 缓存命中，反序列化并返回
            List<ShopType> typeList = JSONUtil.toList(json, ShopType.class);
            return Result.ok(typeList);
        }

        // 3. 缓存未命中，查数据库
        List<ShopType> typeList = typeService.query().orderByAsc("sort").list();

        // 4. 写入 Redis
        stringRedisTemplate.opsForValue().set(
                CATEGORY_KEY,
                JSONUtil.toJsonStr(typeList),
                30, TimeUnit.MINUTES // 可设置过期时间
        );

        return Result.ok(typeList);
    }
}

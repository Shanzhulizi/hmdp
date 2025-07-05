package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry){

        //把拦截器配置到拦截器的注册器中
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
                .excludePathPatterns(
                  //排除不需要拦截的逻辑

                    "/user/code",
                    "/user/login",
                        "/blog/hot",
                        "/shop/**",//不拦截店铺的所有
                        "/shop-type/**",
                        "/voucher/**"//优惠券查询 不必校验
                );
    }

}

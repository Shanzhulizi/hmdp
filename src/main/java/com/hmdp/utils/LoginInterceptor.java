package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HttpServletBean;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录校验的拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;
    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //获取session
//        HttpSession session = request.getSession();
//
//        Object user = session.getAttribute("user");
//
//        if(user==null){
////            不存在，拦截
//            response.setStatus(401);
//            return false;
//        }
//
//        //存在，保存用户信息到ThreadLocal中
//        UserDTO userDTO=new UserDTO();
//        userDTO.setNickName(((User) user).getNickName());
//        userDTO.setId(((User) user).getId());
//        userDTO.setIcon(((User) user).getIcon());
////        Long id;
////        private String nickName;
////        private String icon;
//        UserHolder.saveUser(userDTO);
//
//        //放行
//        return true;

        //=======================================================

        //用redis替代ssession

        //获取请求头中的token
        String token = request.getHeader("authorization");
        if (token == null || token.isEmpty()) {
            //不存在，拦截
            response.setStatus(401);
            return false;
                    }
        //存在，查询redis
        String key=RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if(userMap.isEmpty()){
            //不存在，拦截
            response.setStatus(401);
            return false;
        }
        //存在，转换为UserDTO
        UserDTO userDTO = new UserDTO();

        BeanUtil.fillBeanWithMap(userMap, userDTO, false); // false表示不覆盖null值

        //保存用户信息到ThreadLocal中
        UserHolder.saveUser(userDTO);

        //刷新token有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        return true;
    }



    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        //移除用户，避免内存泄露
        UserHolder.removeUser();
    }

}

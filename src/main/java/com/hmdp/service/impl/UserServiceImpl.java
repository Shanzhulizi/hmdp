package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
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
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

//    继承自RedisTemplate<String, String>，是专门为键和值都是String类型设计的子类
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
//        1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
//        2.生成验证码
        String code = RandomUtil.randomNumbers(6);
//        3.保存到session
//        session.setAttribute("code", code);

//        3.保存到redis ,2分钟有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        4.发送验证码
        //TODO 只是模拟，要用阿里云付钱的
        log.debug("发送验证码： {}", code);

        return Result.ok();

    }

    /**
     * 如果用验证码登录，那就注册和登录合二为一，密码登录就不是，如果遇到未注册用户，直接返回错
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        String password = loginForm.getPassword();

        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号不对");
        }
        if (password != null) {
            //是密码校验

            Object passwordInSession = session.getAttribute("password");

            // TODO 这里的逻辑暂时不做
            return Result.ok();
        }
//        校验验证码
//        Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        if (cacheCode == null) {
            return Result.fail("验证码已过期，请重新发送");
        }
        if (code == null || !code.equals(cacheCode) ){
            return Result.fail("验证码错误");
        }

//        判断用户是否存在
        //获取用户
        //mybatis-plus
        User user = query().eq("phone", phone).one();

//        存在则登录成功，不存在则创建用户然后登录成功
        if (user == null) {
            //创建用户，密码为空，昵称随机
            user = createUserWithPhone(phone);
        }

//        session.setAttribute("user", user);

        //保存用户到redis
        String token = UUID.randomUUID().toString(true);

        UserDTO userDTO= BeanUtil.copyProperties(user,UserDTO.class);

//        Map<String ,Object> userMap = BeanUtil.beanToMap(userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new LinkedHashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true) //忽略null值
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()) //将所有值转换为字符串
        );

        String tokenKey=LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);

        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.SECONDS);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user =new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants. USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10) );

        //保存用户
        save(user);
        return user;
    }
}

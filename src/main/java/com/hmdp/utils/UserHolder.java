package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

/**
 * 用户上下文持有类：用于在当前线程中存储和获取用户信息
 * 基于ThreadLocal实现，确保多线程环境下用户信息的隔离性
 */
public class UserHolder {
    /**
     * ThreadLocal实例，泛型为UserDTO（用户数据传输对象）
     * ThreadLocal是线程本地变量，每个线程拥有独立的副本，互不干扰
     */
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    /**
     * 存储用户信息到当前线程的ThreadLocal中
     * @param user 用户数据传输对象（包含用户ID、用户名等信息）
     */
    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    /**
     * 从当前线程的ThreadLocal中获取用户信息
     * @return 当前线程存储的UserDTO对象，若未存储则返回null
     */
    public static UserDTO getUser(){
        return tl.get();
    }

    /**
     * 移除当前线程ThreadLocal中存储的用户信息
     * 【重要】使用后必须调用此方法清理，避免内存泄漏和线程复用导致的信息错乱
     */
    public static void removeUser(){
        tl.remove();
    }
}

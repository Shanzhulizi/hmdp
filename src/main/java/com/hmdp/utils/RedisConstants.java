package com.hmdp.utils;


/**
 * Redis常量类：统一管理Redis中使用的键前缀和过期时间
 * 作用：避免硬编码、统一维护、提高可读性，防止键名冲突
 */
public class RedisConstants {

    // ========================== 登录相关常量 ==========================
    /**
     * 登录验证码的键前缀
     * 完整键名格式：login:code:手机号（如 login:code:13800138000）
     * 用于存储手机验证码，value通常为验证码字符串
     */
    public static final String LOGIN_CODE_KEY = "login:code:";

    /**
     * 登录验证码的过期时间（单位：分钟）
     * 表示验证码在2分钟内有效，过期后需重新获取
     */
    public static final Long LOGIN_CODE_TTL = 2L;

    /**
     * 登录用户信息的键前缀
     * 完整键名格式：login:token:用户令牌（如 login:token:eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...）
     * 用于存储登录用户的会话信息（如用户ID、权限等）
     */
    public static final String LOGIN_USER_KEY = "login:token:";

    /**
     * 登录用户信息的过期时间（单位：秒）
     * 36000秒 = 10小时，表示用户登录状态10小时内有效
     * 通常会在用户活跃操作时刷新此过期时间
     */
    public static final Long LOGIN_USER_TTL = 36000L;


    // ========================== 缓存防护相关常量 ==========================
    /**
     * 缓存空值的过期时间（单位：分钟）
     * 用于解决缓存穿透问题：当查询的数据不存在时，缓存一个空值
     * 2分钟后自动失效，避免长期缓存空值导致数据不一致
     */
    public static final Long CACHE_NULL_TTL = 2L;


    // ========================== 商铺缓存相关常量 ==========================
    /**
     * 商铺信息的缓存键前缀
     * 完整键名格式：cache:shop:商铺ID（如 cache:shop:1001）
     * 用于缓存商铺的基本信息（名称、地址、营业时间等）
     */
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    /**
     * 商铺信息的缓存过期时间（单位：分钟）
     * 表示商铺数据在Redis中缓存30分钟，过期后需从数据库重新加载
     */
    public static final Long CACHE_SHOP_TTL = 30L;


    // ========================== 分布式锁相关常量 ==========================
    /**
     * 商铺操作的分布式锁键前缀
     * 完整键名格式：lock:shop:商铺ID（如 lock:shop:1001）
     * 用于并发场景下的资源控制（如更新商铺信息时防止并发修改）
     */
    public static final String LOCK_SHOP_KEY = "lock:shop:";

    /**
     * 商铺分布式锁的过期时间（单位：秒）
     * 防止锁持有者因异常未释放锁导致死锁，10秒后自动释放锁
     */
    public static final Long LOCK_SHOP_TTL = 10L;


    // ========================== 业务功能相关常量 ==========================
    /**
     * 秒杀商品库存的键前缀
     * 完整键名格式：seckill:stock:商品ID（如 seckill:stock:2001）
     * 用于存储秒杀活动中商品的实时库存，支持高并发下的库存扣减
     */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    /**
     * 博客点赞的键前缀
     * 完整键名格式：blog:liked:博客ID（如 blog:liked:3001）
     * 通常用Set类型存储点赞该博客的用户ID集合
     */
    public static final String BLOG_LIKED_KEY = "blog:liked:";

    /**
     * 动态流（Feed流）的键前缀
     * 完整键名格式：feed:用户ID（如 feed:4001）
     * 用于存储用户关注的动态列表，支持实时推送或拉取
     */
    public static final String FEED_KEY = "feed:";

    /**
     * 商铺地理位置的键前缀
     * 完整键名格式：shop:geo:城市标识（如 shop:geo:beijing）
     * 基于Redis的GEO类型存储，用于实现"附近的商铺"等地理位置相关功能
     */
    public static final String SHOP_GEO_KEY = "shop:geo:";

    /**
     * 用户签到的键前缀
     * 完整键名格式：sign:用户ID（如 sign:5001）
     * 通常用Bitmap类型存储用户每日签到状态，用于统计连续签到天数等
     */
    public static final String USER_SIGN_KEY = "sign:";
}

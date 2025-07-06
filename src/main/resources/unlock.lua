-- 判断 Redis 中存储的锁标识是否与当前线程的标识一致
if redis.call('get', KEYS[1]) == ARGV[1] then
    -- 一致则删除锁，返回 1 表示解锁成功
    return redis.call('del', KEYS[1])
else
    -- 不一致则不做操作，返回 0 表示解锁失败
    return 0
end
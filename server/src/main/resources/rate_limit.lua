-- 滑动窗口限流（ZSet + Lua，统计与写入必须原子完成）
-- KEYS[1] 限流 key
-- ARGV[1] 当前时间戳（毫秒）
-- ARGV[2] 窗口大小（毫秒）
-- ARGV[3] 窗口内最大请求数
-- ARGV[4] 本次请求唯一标识
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

-- 1. 移除窗口外的历史请求
redis.call('zremrangebyscore', key, 0, now - window)
-- 2. 统计窗口内请求数
local count = redis.call('zcard', key)
-- 3. 超限直接拒绝
if count >= limit then
    return 0
end
-- 4. 记录本次请求并给 key 续期
redis.call('zadd', key, now, ARGV[4])
redis.call('pexpire', key, window)
return 1
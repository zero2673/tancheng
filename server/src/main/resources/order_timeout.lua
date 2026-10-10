-- 订单超时延时队列：原子地取出到期订单号，并从 ZSet 中移除
-- KEYS[1] delay:order
-- ARGV[1] 当前时间戳（毫秒）
-- ARGV[2] 本次最多取多少条
local expired = redis.call('zrangebyscore', KEYS[1], 0, ARGV[1], 'LIMIT', 0, ARGV[2])
if #expired > 0 then
    redis.call('zrem', KEYS[1], unpack(expired))
end
return expired
--参数列表
--优惠卷id
--local voucherId = ARGV[1]
----用户id
--local userId = ARGV[1]
----数据key
----库存
--local stockKey = 'seckill:stock:' .. voucherId
----订单
--local orderKey = 'seckill:order:' .. voucherId
----脚本业务
----判断库存是否充足
--if(tonumber(redis.call('get', stockKey)) <= 0) then
--    --库存不足
--    return 1
--end
----判断用户是否已经购买过
--if(redis.call('sismember', orderKey, userId) == 1) then
--    --用户已经购买过
--    return 2
--end
----扣减库存
--redis.call('incrby', stockKey, -1)
----添加到已购买用户集合
--redis.call('sadd', orderKey, userId)

--利用消息队列stream
--Redis中需要开启消费者组，名为stream.orders
--XGROUP CREATE stream.orders g1 0 MKSTREAM
--参数列表
--优惠卷id
local voucherId = ARGV[1]
--用户id
local userId = ARGV[2]
local orderID= ARGV[3]
--数据key
--库存
local stockKey = 'seckill:stock:' .. voucherId
--订单
local orderKey = 'seckill:order:' .. voucherId
--脚本业务
--判断库存是否充足
if(tonumber(redis.call('get', stockKey)) <= 0) then
    --库存不足
    return 1
end
--判断用户是否已经购买过
if(redis.call('sismember', orderKey, userId) == 1) then
    --用户已经购买过
    return 2
end
--扣减库存
redis.call('incrby', stockKey, -1)
--添加到已购买用户集合
redis.call('sadd', orderKey, userId)
--发送消息到队列中
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderID)
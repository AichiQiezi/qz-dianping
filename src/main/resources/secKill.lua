--声明参数，注意顺序
-- 1.1.优惠券id
local voucherId = ARGV[1]
-- 1.2.用户id
local userId = ARGV[2]
-- 1.3.订单id
local orderId = ARGV[3]

-- 2 数据key
--数据库的 key
local stockKey = 'secKill:stock:' .. voucherId
-- 订单的key
local orderKey = 'secKill:order:' .. voucherId

-- 3 脚本业务
-- 3.1 判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    --库存不足，返回1
    return 1
end
-- 3.2 判断用户是否下单,zset结构 sismember判断集合是否包含某元素，true 返回 1
if (redis.call('sismember', orderKey, userId) == 1) then
    --用户已经下单，返回2
    return 2
end

-- 3.3 扣库存 Incrby 命令将 key 中储存的数字加上指定的增量值（拓展：如果key不存在就会默认赋值为0）
redis.call('incrby', stockKey, -1)
-- 3.4 下单保存用户（为了防止用户重复下单）
redis.call('sadd', orderKey, userId)
-- 3.5 发送消息到消息队列 XADD stream.orders * k1 v1 k2 v2 ...(参数名最好与实体类属性对应，方便反序列化)
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)

return 0
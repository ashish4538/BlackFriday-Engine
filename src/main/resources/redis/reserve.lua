-- Validate before writes: script errors do not roll back earlier commands.
local existing = redis.call('GET', KEYS[2])
if existing then return existing end
local raw = redis.call('GET', KEYS[1])
if not raw then return 'UNINITIALIZED' end
local stock = tonumber(raw)
if not stock or stock < 0 or stock ~= math.floor(stock) then
    return redis.error_reply('Invalid stock counter')
end
if stock == 0 then return 'SOLD_OUT' end
redis.call('DECR', KEYS[1])
redis.call('SET', KEYS[2], ARGV[1])
return ARGV[1]

local key = KEYS[1]
local count = tonumber(ARGV[1])
local members = redis.call('ZRANGEBYSCORE', key, '-inf', '+inf', 'LIMIT', 0, count)
if #members == 0 then
    return {}
end

local result = {}
for i, m in ipairs(members) do
    local score = redis.call('ZSCORE', key, m)
    result[#result + 1] = m
    result[#result + 1] = tostring(score)
end

redis.call('ZREM', key, unpack(members))
return result

--判断标识是否与锁中标识一致。
if(redis.call('get',KEYS[1])==ARGV[1]) then
    return redis.call('del',KEYS[1])
end
return 0
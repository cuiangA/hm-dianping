--限制每个用户只能点赞一次--
local blogId = ARGV[1]
local userId = ARGV[2]
local likesTime = ARGV[3]
local blogKey = "blog:blogId:"..blogId
if (redis.call("zscore",blogKey,userId) == false) then
    redis.call('zadd',blogKey,tonumber(likesTime),userId)
    return 0;
end
return 1;
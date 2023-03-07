package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.Data;

import java.time.LocalDateTime;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}

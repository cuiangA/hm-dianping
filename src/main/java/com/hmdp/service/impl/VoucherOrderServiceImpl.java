
package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private static final Logger log = LoggerFactory.getLogger(VoucherOrderServiceImpl.class);
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    @Lazy
    private IVoucherOrderService voucherOrderService;
    private static final DefaultRedisScript<Long> SECKILL_LUA = new DefaultRedisScript();
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue(1048576);
    private ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private IVoucherOrderService proxy;

    public VoucherOrderServiceImpl() {
    }

    @PostConstruct
    private void init() {
        this.SECKILL_ORDER_EXECUTOR.submit(new voucherOrderHandler());
    }

    @Transactional
    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, this.stringRedisTemplate);
        if (!lock.tryLock(5L)) {
            log.error("不能重复下单");
        } else {
            try {
                this.proxy.createVoucherOther(voucherOrder);
            } catch (Exception var8) {
                throw new RuntimeException(var8);
            } finally {
                lock.delLock();
            }

        }
    }

    @Transactional
    public Result seckKillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long result = (Long)this.stringRedisTemplate.execute(SECKILL_LUA, Collections.emptyList(), new Object[]{voucherId.toString(), userId.toString()});

        assert result != null;

        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复购买");
        } else {
            Long orderId = this.redisIdWorker.nextId("order");
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(orderId);
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setUserId(userId);
            this.proxy = (IVoucherOrderService)AopContext.currentProxy();
            this.orderTasks.add(voucherOrder);
            return Result.ok(orderId);
        }
    }

    @Transactional
    public void createVoucherOther(VoucherOrder voucherOrder) {
        boolean success = seckillVoucherService.lambdaUpdate()
                .setSql("stock=stock-1")
                .eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId())
                .gt(SeckillVoucher::getStock, 0)
                .update();
        if (!success) {
            log.error("库存不足");
        }

        this.save(voucherOrder);
    }

    static {
        SECKILL_LUA.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_LUA.setResultType(Long.class);
    }

    private class voucherOrderHandler implements Runnable {
        private voucherOrderHandler() {
        }

        public void run() {
            while(true) {
                try {
                    VoucherOrder voucherOrder = (VoucherOrder)VoucherOrderServiceImpl.this.orderTasks.take();
                    VoucherOrderServiceImpl.this.handleVoucherOrder(voucherOrder);
                } catch (Exception var2) {
                    VoucherOrderServiceImpl.log.info("系统处理订单异常：", var2);
                }
            }
        }
    }
}
package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    @Lazy
    private IVoucherOrderService voucherOrderService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Override
    @Transactional
    public Result seckKillVoucher(Long voucherId) {
        //查询优惠劵信息
        //查询优惠是否开启
        VoucherOrder voucherOrder = new VoucherOrder();
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("优惠券未到使用时间");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("优惠券已过期");
        }
        if (seckillVoucher.getStock()<1) {
            return Result.fail("来晚了，没有优惠券啦");
        }
        //
        Long userID = UserHolder.getUser().getId();
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userID, stringRedisTemplate);
        //使用简单的redis的分布式锁
        if (!lock.tryLock(5L)){
            return Result.fail("不能重复下单");
        }
        try {
            return voucherOrderService.createVoucherOther(voucherId, voucherOrder);
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            lock.delLock();
        }
    }
    @Transactional
    @Override
    public Result createVoucherOther(Long voucherId, VoucherOrder voucherOrder) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        LambdaQueryWrapper<VoucherOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VoucherOrder::getUserId,userId)
                .eq(VoucherOrder::getVoucherId , voucherId);
        VoucherOrder oneOrder = getOne(queryWrapper);
        if (oneOrder!=null){
            return Result.fail("该优惠券每个用户仅限购买一次");
        }
        //执行update时再次判断stock是否大于0避免超卖问题
        boolean success = seckillVoucherService.lambdaUpdate()
                .setSql("stock=stock-1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock,0)
                .update();
        if (!success){
            return Result.fail("库存不足");
        }
        Long id = redisIdWorker.nextId("order");

        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(id);
        save(voucherOrder);
        stringRedisTemplate.opsForValue().set(SECKILL_ORDER_KEY+voucherId,userId.toString());
        return Result.ok(id);
    }
}

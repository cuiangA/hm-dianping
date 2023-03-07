package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("非法手机号");
        }
        //生成随机数
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送省去
        log.info("手机验证码：{}",code);
        return Result.ok("success");
    }

    @Override
    public Result validateLogin(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("非法手机号");
        }
        //判断用户是否第一次登录
        User user = lambdaQuery().eq(User::getPhone, phone).one();
        //校验密码或验证码
        String code = loginForm.getCode();
        String password = loginForm.getPassword();
        if (code!=null){//校验验证码

            String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
            if (!code.equals(cacheCode)){
                return Result.fail("验证码错误");
            }
            if (user==null){
                user = createUserWithPhone(phone);
            }

        }else {//密码登录
            //检验用户名是否存在
            if (user==null){
                return Result.fail("用户名不存在");
            }
            //校验密码
            if (password==null|| !password.equals(user.getPassword())){
                return Result.fail("密码错误");
            }
        }
        //将部分信息放置到DTO类中
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                                    .setFieldValueEditor((filedName,filedValue)->filedValue.toString()));
        //生成随机的token
        String token = UUID.randomUUID().toString(true);
        //存储到redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //设置存活时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result queryUser(String id) {
        User user = getById(id);
        if (user==null){
            return Result.fail("用户不存在");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}

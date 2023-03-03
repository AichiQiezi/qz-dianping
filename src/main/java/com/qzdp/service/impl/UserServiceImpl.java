package com.qzdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qzdp.dto.LoginFormDTO;
import com.qzdp.dto.Result;
import com.qzdp.dto.UserDTO;
import com.qzdp.entity.User;
import com.qzdp.mapper.UserMapper;
import com.qzdp.service.IUserService;
import com.qzdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.qzdp.utils.BaseConstants.SIXTY;
import static com.qzdp.utils.BaseConstants.UNDERLINE;
import static com.qzdp.utils.RedisConstants.*;
import static com.qzdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * todo 接口防刷
     *
     * @param phone   用户手机号
     * @param session session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号是否正确
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不对哦！");
        }
        //2.1   判断此手机号是否在60秒内重复获取验证码
        String redisValue = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (!StringUtils.isEmpty(redisValue)) {
            long oldTime = Long.parseLong(redisValue.split(UNDERLINE)[1]);
            if ((System.currentTimeMillis() - oldTime < SIXTY)) {
                return Result.fail("不要频繁获取验证码哦！");
            }
        }
        //2.2生成验证码，并保存到session中去，并再验证码后记录请求验证码的时间
        String code = RandomUtil.randomNumbers(4);
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code + "_" + System.currentTimeMillis(),
                LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //3.发送给用户，用日志代替了
        log.info("您好，您的验证码为：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //获取用户输入的信息并进行校验
        String phone = loginForm.getPhone();
        //1.先检验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        //2.根据手机号获取验证码并进行校验
        String redisValue = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        //2.1验证为空
        if (StringUtils.isEmpty(redisValue)) {
            return Result.fail("验证码已过期！");
        }
        //2.2验证码不一致
        if (!redisValue.split(UNDERLINE)[0].equals(loginForm.getCode())) {
            return Result.fail("验证码不一致！");
        }
        //删除验证码。。todo
        //3.判断此用户是否存在
        User user = query().eq("phone", phone).one();
        if (user == null) {
            //此用户不存在，直接创建新用户进行保存
            user = createUserWithPhone(phone);
        }
        //4.保存用户信息到redis中
        //4.1随机生成token，做为登录令牌,保存到redis中去
        String token = UUID.randomUUID().toString(true);
        String tokenKey = LOGIN_USER_KEY + token;
        //4.2将user对象转化为hashMap进行存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(3),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //putAll会把map数据全部存放
        redisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 5.设置token有效期
        redisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //6.把token返回给前端
        return Result.ok(token);
    }

    /**
     * 创建用户
     *
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户
        save(user);
        return user;
    }
}

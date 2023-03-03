package com.qzdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.qzdp.dto.UserDTO;
import com.qzdp.utils.RedisConstants;
import com.qzdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author haofeng
 * @date 2022/10/13 16:19
 * @description 负责拦截所有的路径，主要是刷新 token
 *  职责：
 *      - 获取 token
 *      - 根据 token来到 redis查询用户
 *      - 把 user对象保存到 threadLocal中
 *      - 刷新 token的有效期
 *      - 放行
 */

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)){
            return true;
        }
        //2.基于token来从redis中获取用户信息
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(key);
        //3.判断用户是否存在
        if (userMap.isEmpty()){
            //清空userHolder中保存的用户信息
            UserHolder.removeUser();
            return true;
        }
        //4.hutool工具类把userMap对象转化为Javabean,转化为userDTO是为了减少存储空间和泄露风险
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //5.把userDTO信息保存到threadLocal中
        UserHolder.saveUser(userDTO);
        //6.刷新token的有效期
        redisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }
}

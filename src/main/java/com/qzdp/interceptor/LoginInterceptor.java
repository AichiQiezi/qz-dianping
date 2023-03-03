package com.qzdp.interceptor;

import com.qzdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author haofeng
 * @date 2022/10/13 16:16
 * @description 登录拦截器，拦截只需要登录的路径
 *  用户如果登录的话，就存放在threadLocal中去
 *  职责：
 *      - 判断threadLocal中 user 对象是否为空
 *          为空：去登录
 *          不为空：放行
 */

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (UserHolder.getUser() == null){
            //401 没有认证即没权限
            response.setStatus(401);
            return false;
        }
        return true;
    }
}

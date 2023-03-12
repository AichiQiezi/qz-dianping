package com.qzdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qzdp.dto.LoginFormDTO;
import com.qzdp.dto.Result;
import com.qzdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *

 */
public interface IUserService extends IService<User> {
    /**
     * 发送验证码，并且保存到session session的实现方式选择redis中
     * @param phone 用户手机号
     * @param session session
     * @return 结果
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 实现登录功能
     * @param loginForm 用户输入的登录信息
     * @param session
     * @return 返回检验结果
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}

package com.AFF.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.AFF.dto.LoginFormDTO;
import com.AFF.dto.Result;
import com.AFF.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author umbrellazg
 * @since 2024-11-22
 */
public interface IUserService extends IService<User> {


    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}

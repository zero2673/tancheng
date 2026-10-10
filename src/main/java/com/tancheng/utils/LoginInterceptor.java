package com.tancheng.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.tancheng.dto.UserDTO;
import com.tancheng.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//拦截器
public class LoginInterceptor implements HandlerInterceptor {



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//1.判断是否需要拦截
if(UserHolder.getUser()==null){
    //2.需要拦截，设置状态码
    response.setStatus(401);
    //3.拦截
    return false;
}
//有用户，放行

        return true;
    }



}

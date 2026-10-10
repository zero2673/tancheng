package com.tancheng.controller;


import cn.hutool.core.bean.BeanUtil;
import com.tancheng.dto.LoginFormDTO;
import com.tancheng.dto.Result;
import com.tancheng.dto.UserDTO;
import com.tancheng.entity.User;
import com.tancheng.entity.UserInfo;
import com.tancheng.service.IUserInfoService;
import com.tancheng.service.IUserService;
import com.tancheng.service.RateLimitService;
import com.tancheng.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private RateLimitService rateLimitService;

    /**
     * 发送手机验证码
     */
        /**
     * 发送手机验证码。IP 维度限流：同一 IP 每分钟最多 10 次
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone,
                           HttpSession session,
                           HttpServletRequest request) {
        String ip = getClientIp(request);
        if (!rateLimitService.tryAcquire("limit:code:ip:" + ip, 10, 60_000L)) {
            return Result.fail("操作过于频繁，请稍后再试");
        }
        return userService.sendCode(phone, session);
    }

    /**
     * 取真实客户端 IP（过 Nginx 时要看 X-Forwarded-For / X-Real-IP）
     */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
//283583 fbf881e0805a4c0ea48711bed27d50f5
    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // TODO 实现登录功能
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        // TODO 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
    //  根据id查询用户
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }

}

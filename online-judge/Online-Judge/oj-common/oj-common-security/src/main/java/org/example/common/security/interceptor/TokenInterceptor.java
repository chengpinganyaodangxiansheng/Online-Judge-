package org.example.common.security.interceptor;

import cn.hutool.core.util.StrUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.common.core.constants.Constants;
import org.example.common.core.constants.HttpConstants;
import org.example.common.core.utils.ThreadLocalUtil;
import org.example.common.security.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TokenInterceptor implements HandlerInterceptor {
    @Autowired
    private TokenService tokenService;

    @Value("${jwt.secret}")
    private String secret;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //方法开始前调用拦截器
        String token = getToken(request);
        if (token == null) {
            return true;
        }


        Claims claims = tokenService.getClaims(token, secret);
        Long userId = tokenService.getUserId(claims);
        String userKey = tokenService.getUserKey(claims);

        //使用线程本地变量来存储该线程的userID
        ThreadLocalUtil.set(Constants.USER_ID, userId);
        ThreadLocalUtil.set(Constants.USER_KEY, userKey); 

        tokenService.extendToken(claims);
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //方法结束后，自动调用拦截器
        ThreadLocalUtil.remove();
    }

    /**
     * 从请求头中获取请求token
     */
    private String getToken(HttpServletRequest request) {
        //getHeader获取头部的信息，
        String token = request.getHeader(HttpConstants.AUTHENTICATION);

        // 如果前端设置了令牌前缀，则裁剪掉前缀
        if (StrUtil.isNotEmpty(token) && token.startsWith(HttpConstants.PREFIX)) {
            //删除出现token中HttpConstants.PREFIX的前缀，并按照StrUtil.EMPTY(空字符串)替换，是为了得到纯净的token
            token = token.replaceFirst(HttpConstants.PREFIX, StrUtil.EMPTY);
        }
        return token;
    }
}

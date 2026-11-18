package org.example.common.security.service;


import cn.hutool.core.lang.UUID;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.example.common.core.constants.CacheConstants;
import org.example.common.core.constants.JwtConstants;
import org.example.common.redis.service.RedisService;
import org.example.common.core.domain.LoginUser;
import org.example.common.core.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TokenService {

    @Autowired
    private RedisService redisService;

    //操作用户的token方法
    public String createToken(Long userId,String secret,Integer identity,String nikeName,String headImage) {
        //通过Jwt令牌进行身份认证
        Map<String, Object> claims = new HashMap<>();//存储用户的唯一标识
        String userKey = UUID.fastUUID().toString();//存储用户的UUID，当该token未过期时，在redis中可以找到数据
        claims.put(JwtConstants.LOGIN_USER_ID, userId);
        claims.put(JwtConstants.LOGIN_USER_KEY, userKey);//使用UUID生成的唯一标识+前缀作为key来查询信息，是为了防止userId被设置为了增主键情况下，没办法区分用户
        String token = JwtUtils.createToken(claims,secret);//secret通过在nacos上进行配置


        //在redis中使用String类型存放用户的敏感数据(redis的key是userKey+前缀，userKey则是通过UUID生成的，即可通过解析token时获取，便可以判断是否存在用户的敏感信息)
        String key = getTokenKey(userKey);

        //敏感数据  -> 用户的身份
        LoginUser loginUser = new LoginUser();
        loginUser.setIdentity(identity);
        loginUser.setNickName(nikeName);
        loginUser.setHeadImage(headImage);
        //生成key value
        redisService.setCacheObject(key,loginUser, CacheConstants.EXP, TimeUnit.MINUTES);

        return token;
    }

    //延长token的有效时长,就是延长redis当中存储用户身份认证的敏感信息的有效时长
    public void extendToken(Claims claims) {
//        Claims claims = null;
//        try {
//            claims = JwtUtils.parseToken(token,secret);//获取jwt令牌中的信息，解析payload，存储用户唯一标识信息
//            if (claims == null) {
//                log.error("解析token:{}, 出现异常",token);
//                return;
//            }
//        }catch (Exception e) {
//            log.error("解析token:{}, 出现异常",token,e);
//            return;
//        }
//
//
//        String userKey = JwtUtils.getUserKey(claims);//获取jwt中的key,这个key是UUID生成的
        String userKey = getUserKey(claims);//获取用户登录是存放在LoginUserKey中的UUID的值
        String tokenKey = getTokenKey(userKey);//这个tokenKey才是存储在redis中的key值(这个LoginTokenKey是通过tokenKey+UUID拼接的)

        //获取当前token的过期时间,并重新设置新的过期时间
        Long expire = redisService.getExpire(tokenKey, TimeUnit.MINUTES);
        if (expire != null && expire < CacheConstants.REFRESH_TIME) {
            redisService.expire(tokenKey,CacheConstants.EXP, TimeUnit.MINUTES);
        }


    }

    public LoginUser getLoginUser(String token, String secret) {
        String userKey = getUserKey(token,secret);
        if (userKey == null) {
            return null;
        }
        return redisService.getCacheObject(getTokenKey(userKey), LoginUser.class);
    }

    private String getTokenKey(String userKey) {
        return CacheConstants.LOGIN_TOKEN_KEY + userKey;
    }

    public String getUserKey( Claims claims) {
        if (claims == null) return null;
        return JwtUtils.getUserKey(claims);//获取jwt中的key,这个key是UUID生成的
    }

    private String getUserKey(String token,String secret) {
        Claims claims = getClaims(token, secret);
        if (claims == null) return null;
        return JwtUtils.getUserKey(claims);//获取jwt中的key的值,这个key是UUID生成的
    }

    public  Claims getClaims(String token, String secret) {
        Claims claims = null;
        try {
            claims = JwtUtils.parseToken(token, secret);//获取jwt令牌中的信息，解析payload，存储用户唯一标识信息
            if (claims == null) {
                log.error("解析token:{}, 出现异常", token);
                return null;
            }
        }catch (Exception e) {
            log.error("解析token:{}, 出现异常", token,e);
            return null;
        }
        return claims;
    }

    public Long getUserId(Claims claims) {
        if (claims == null) return null;
        return Long.valueOf(JwtUtils.getUserId(claims));
    }


    public Long getUserId(String token, String secret) {
        Claims claims = getClaims(token, secret);
        if (claims == null) return null;
        return Long.valueOf(JwtUtils.getUserId(claims));
    }

    public boolean deleteLoginUser(String token,String secret) {
        String userKey = getUserKey(token,secret);
        if (userKey == null) {
            return false;
        }
        //删除redis中token的缓存
        return redisService.deleteObject(getTokenKey(userKey));
    }

    public void refreshLoginUser(String nickName, String headImage, String userKey) {
        //获取tokenKey
        String tokenKey = getTokenKey(userKey);
        //从redis缓存中获取登录用户的详情信息
        LoginUser loginUser = redisService.getCacheObject(tokenKey, LoginUser.class);
        //对当前用户的详情信息进行修改
        loginUser.setNickName(nickName);
        loginUser.setHeadImage(headImage);
        //刷新redis缓存中的用户详情信息
        redisService.setCacheObject(tokenKey, loginUser);
    }
}

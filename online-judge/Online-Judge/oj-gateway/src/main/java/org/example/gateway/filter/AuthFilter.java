package org.example.gateway.filter;


import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.example.common.core.constants.CacheConstants;
import org.example.common.core.constants.Constants;
import org.example.common.core.constants.HttpConstants;
import org.example.common.core.domain.Result;
import org.example.common.core.enums.ResultCode;
import org.example.common.core.enums.UserIdentity;
import org.example.common.core.utils.JwtUtils;
import org.example.common.core.utils.ThreadLocalUtil;
import org.example.common.redis.service.RedisService;
import org.example.common.core.domain.LoginUser;
import org.example.gateway.properties.IgnoreWhiteProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.List;

@Slf4j
@Component
public class AuthFilter implements GlobalFilter, Ordered {
    //排除过滤的 uri ⽩名单地址，在nacos⾃⾏添加
    @Autowired
    private IgnoreWhiteProperties ignoreWhite;
    @Value("${jwt.secret}")
    private String secret;
    @Autowired
    private RedisService redisService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain){
        ServerHttpRequest request = exchange.getRequest();
        String url = request.getURI().getPath();

        // 跳过不需要验证的路径(例如登录接口)
        if (matches(url, ignoreWhite.getWhites())) {
            return chain.filter(exchange);
        }

        //从http请求头中获取token
        String token = getToken(request);
        if (StrUtil.isEmpty(token)) {
            //自定义的全局异常通过@RestControllerAdvice注解修饰后，只能使用于Spring MVC，而不能在Spring cloud gateway中使用，那么就需要使用webflux来构造
           return unauthorizedResponse(exchange, "令牌不能为空");
        }

        Claims claims;
        try {
            claims = JwtUtils.parseToken(token, secret); //获取令牌中信息 解析payload中信息
            if (claims == null) {
                return unauthorizedResponse(exchange, "令牌已过期或验证不正确！");
            }
        } catch (Exception e) {
            return unauthorizedResponse(exchange, "令牌已过期或验证不正确！");
        }

        String userKey = JwtUtils.getUserKey(claims); //获取jwt中的key

        //在此处来获取redis中存放的用户token，如果存在则代表token有效。不存在则代表登录状态过期了
        boolean isLogin = redisService.hasKey(getTokenKey(userKey));
        if (!isLogin) {
            return unauthorizedResponse(exchange, "登录状态已过期");
        }

        String userId = JwtUtils.getUserId(claims); //判断jwt中的信息是否完整
        if (StrUtil.isEmpty(userId)) {
            return unauthorizedResponse(exchange, "令牌验证失败");
        }

        LoginUser user = redisService.getCacheObject(getTokenKey(userKey), LoginUser.class);
        if (url.contains(HttpConstants.SYSTEM_URL_PREFIX) && !UserIdentity.ADMIN.getValue().equals(user.getIdentity())) {
            return unauthorizedResponse(exchange, "令牌验证失败");
        }

        if (url.contains(HttpConstants.FRIEND_URL_PREFIX) && !UserIdentity.ORDINARY.getValue().equals(user.getIdentity())) {
            return unauthorizedResponse(exchange, "令牌验证失败");
        }

        //此处即使设置了userId,在另外一个包中也无法获取，因为微服务的包是完全独立的,并且这是网关项目
        //ThreadLocalUtil.set(Constants.USER_ID, userId);
        return chain.filter(exchange);
    }


    /**
     * 查找指定url是否匹配指定匹配规则链表中的任意⼀个字符串
     *
     * @param url 指定url
     * @param patternList 需要检查的匹配规则链表
     * @return 是否匹配
     */
    private boolean matches(String url, List<String> patternList) {
        if (StrUtil.isEmpty(url) || CollectionUtils.isEmpty(patternList)) {
            return false;
        }
        for (String pattern : patternList) {
            if (isMatch(pattern, url)) {
                return true;
            }
        }
        return false;
    }



    /**
     * 判断url是否与规则匹配
     * 匹配规则中：
     * pattern作为匹配规则
     * ? 表⽰单个字符;
     * * 表⽰⼀层路径内的任意字符串，不可跨层级;
     * ** 表⽰任意层路径;
     *
     * @param pattern 匹配规则
     * @param url 需要匹配的url
     * @return 是否匹配
     */
    private boolean isMatch(String pattern, String url) {
        AntPathMatcher matcher = new AntPathMatcher();
        return matcher.match(pattern, url);
    }


    /**
     * 获取缓存key
     */
    private String getTokenKey(String token) {
        return CacheConstants.LOGIN_TOKEN_KEY + token;
    }


    /**
     * 从请求头中获取请求token
     */
    private String getToken(ServerHttpRequest request) {
        //getHeaders()获取HTTP请求的所有头部信息，getFirst(HttpConstants.AUTHENTICATION)获取指定头部的第一个值
        String token = request.getHeaders().getFirst(HttpConstants.AUTHENTICATION);
        // 如果前端设置了令牌前缀，则裁剪掉前缀
        if (StrUtil.isNotEmpty(token) && token.startsWith(HttpConstants.PREFIX)) {
            token = token.replaceFirst(HttpConstants.PREFIX, StrUtil.EMPTY);
        }
        return token;
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String msg) {
        log.error("[鉴权异常处理]请求路径:{}", exchange.getRequest().getPath());
        return webFluxResponseWriter(exchange.getResponse(), msg, ResultCode.FAILED_UNAUTHORIZED.getCode());
    }


    //拼装webflux模型响应
    private Mono<Void> webFluxResponseWriter(ServerHttpResponse response, String msg, int code) {
        response.setStatusCode(HttpStatus.OK);
        //设置响应头  通过 response.getHeaders() 获取响应头对象（HttpHeaders），添加 Content-Type 头为 application/json，告知客户端返回的内容是 JSON 格式。
        response.getHeaders()
                .add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        Result result = Result.fail(code, msg);
        DataBuffer dataBuffer = response
                .bufferFactory()//获取 DataBufferFactory，用于创建 WebFlux 中的 DataBuffer（数据缓冲区，类似 NIO 的 ByteBuffer）。
                .wrap(JSON.toJSONString(result).getBytes());//JSON.toJSONString(result)：使用 JSON 库（如 Alibaba Fastjson 或 Jackson）将 result 对象序列化为 JSON 字符串。    将 JSON 字符串转为字节数组（使用平台默认字符集，一般为 UTF-8）。  将字节数组包装成 DataBuffer 对象，以便后续写入响应体。
        return response.writeWith(Mono.just(dataBuffer));//将 Mono 中的数据缓冲区写入响应输出流。此方法返回一个 Mono<Void>，表示当写入完成时发出完成信号。        Mono.just(dataBuffer)：创建一个包含单个 DataBuffer 的 Mono 流。
    }


    @Override
    public int getOrder() {
        return -200;
    }
}

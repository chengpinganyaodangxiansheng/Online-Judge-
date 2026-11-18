package org.example.friend.service.user.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.common.core.constants.CacheConstants;
import org.example.common.core.constants.Constants;
import org.example.common.core.constants.HttpConstants;
import org.example.common.core.domain.LoginUser;
import org.example.common.core.domain.Result;
import org.example.common.core.domain.vo.LoginUserVO;
import org.example.common.core.enums.ResultCode;
import org.example.common.core.enums.UserIdentity;
import org.example.common.core.enums.UserStatus;
import org.example.common.core.utils.ThreadLocalUtil;
import org.example.common.message.service.MailService;
import org.example.common.redis.service.RedisService;
import org.example.common.security.exception.ServiceException;
import org.example.common.security.service.TokenService;
import org.example.friend.domain.user.User;
import org.example.friend.domain.user.dto.UserDTO;
import org.example.friend.domain.user.dto.UserUpdateDTO;
import org.example.friend.domain.user.vo.UserVO;
import org.example.friend.manager.UserCacheManager;
import org.example.friend.mapper.user.UserMapper;
import org.example.friend.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MailService mailService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private UserCacheManager userCacheManager;

    //验证码有效时长
    @Value("${sms.code-expiration:5}")
    private Long emailCodeExpiration;

    //当天获取验证码的有效次数
    @Value("${sms.send-limit:3}")
    private Integer sendLimit;

    @Value("${jwt.secret}")
    private String secret;

    @Autowired
    private TokenService tokenService;

    @Value("${sms.is-send:false}")
    private boolean isSend;  //开关打开：true  开关关闭false

    @Value("${file.oss.downloadUrl}")
    private String downloadUrl;

    /**
     * 发送验证码
     * @param userDTO
     */
    public boolean sendCode(UserDTO userDTO) {
        //判断邮箱是否正确
        if (!checkEmail(userDTO.getEmail())) {
            throw new ServiceException(ResultCode.FAILED_USER_EMAIL);
        }

        //创建出存储能验证码有效时间的key值
        String emailCodeKey = getEmailCodeKey(userDTO.getEmail());
        //获取当前验证码的过期时间,判断再次获取验证码时,是否超过1分钟
        Long expire = redisService.getExpire(emailCodeKey, TimeUnit.SECONDS);
        if (emailCodeKey != null && (emailCodeExpiration * 60 - expire) < 60) {
            throw new ServiceException(ResultCode.FAILED_FREQUENT);
        }

        //创建获取验证码的次数在redis中存储的key值
        String codeTimeKey = getCodeTimeKey(userDTO.getEmail());
        //判断该邮箱获取验证码的次数，不准超过指定的次数
        Long sendTimes = redisService.getCacheObject(codeTimeKey, Long.class);
        if (sendTimes != null && sendTimes > sendLimit) {
            throw new ServiceException(ResultCode.FAILED_TIME_LIMIT);
        }

        //生成一个随机的验证码(判断是否为开发的环境，开放环境尽量避免大量的重复发送邮件验证码)
        String code = isSend ? RandomUtil.randomNumbers(6) : Constants.DEFAULT_CODE;
        //将生成的code放在redis当中进行定期缓存(使用String类型进行存储，Key: e:c:邮箱 value:code)
        redisService.setCacheObject(emailCodeKey,code,emailCodeExpiration, TimeUnit.MINUTES);
        //判断是否为开发环境，当在开发环境时，不需要发送大量验证码邮件
        if (isSend) {
            //发送验证码
            Integer i = mailService.sendTextMailMessage(userDTO.getEmail(), code);
            //判断验证码是否成功发送
            if (i == null) {
                throw new ServiceException(ResultCode.FAILED_SEND_CODE);
            }
        }
        //对该邮箱获取验证码的次数+1
        redisService.increment(codeTimeKey);
        //如果是当天第一次获取数据，则给其当天获取验证码次数的key设置一个过期时间，这个过期时间会在第二天的0时0分0秒结束
        //sendTimes为空，意味着是第一次请求验证码(当天)
        if (sendTimes == null) {
            //设置第二天0时0分0秒到现在的时间差(秒数)
            long seconds = ChronoUnit.SECONDS.between(LocalDateTime.now(),
                    LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0));
            redisService.expire(codeTimeKey, seconds, TimeUnit.SECONDS);
        }
        return true;
    }

    /**
     * 用户登录
     * @param userDTO
     * @return
     */
    public String codeLogin(UserDTO userDTO) {
        //判断验证码是否正确
        checkCode(userDTO.getEmail(),userDTO.getCode());

        //判断用户是新用户还是老用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("email",userDTO.getEmail());
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            user = new User();
            //新用户,并对用户进行数据编辑，需要向数据库中插入数据
            user.setEmail(userDTO.getEmail());
            user.setStatus(UserStatus.Normal.getValue());
            user.setCreateBy(Constants.SYSTEM_USER_ID);
            user.setPhone(userDTO.getCode());
            userMapper.insert(user);
        }
        //无论新老用户都需要将生成的token返回前端
        return tokenService.createToken(user.getUserId(),secret, UserIdentity.ORDINARY.getValue(), user.getNickName(),user.getHeadImage());
    }

    @Override
    public Result logout(String token) {
        // 如果前端设置了令牌前缀，则裁剪掉前缀
        if (StrUtil.isNotEmpty(token) && token.startsWith(HttpConstants.PREFIX)) {
            token = token.replaceFirst(HttpConstants.PREFIX, StrUtil.EMPTY);
        }

        //退出登录时，删除redis中存储的token缓存(token是用户的唯一标识，通过UUID+固定前缀合成的key)
        return tokenService.deleteLoginUser(token,secret) ? Result.success() : Result.fail();
    }

    @Override
    public Result<LoginUserVO> info(String token) {
        // 如果前端设置了令牌前缀，则裁剪掉前缀
        if (StrUtil.isNotEmpty(token) && token.startsWith(HttpConstants.PREFIX)) {
            token = token.replaceFirst(HttpConstants.PREFIX, StrUtil.EMPTY);
        }

        LoginUser loginUser = tokenService.getLoginUser(token,secret);
        if (loginUser == null) {
            return Result.fail();
        }

        LoginUserVO loginUserVO = new LoginUserVO();
        loginUserVO.setNickName(loginUser.getNickName());

        //图形信息不为NULL才进行拼接
        if (StrUtil.isNotEmpty(loginUser.getHeadImage())) {
            loginUserVO.setHeadImage(downloadUrl+loginUser.getHeadImage());
        }
        return Result.success(loginUserVO);
    }

    @Override
    public UserVO detail() {
        //先获取当前用户的userId
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        if (userId == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        //该方法中会先从redis缓存中获取信息，如果不存在，就从数据库中获取用户信息，并且将当前数据缓存到redis中，如果数据库没有信息，则直接返回NULL
        UserVO userVO = userCacheManager.getUserById(userId);
        if (userVO == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }

        if (StrUtil.isNotEmpty(userVO.getHeadImage())) {
            userVO.setHeadImage(downloadUrl + userVO.getHeadImage());
        }
        return userVO;
    }

    @Override
    public int edit(UserUpdateDTO userUpdateDTO) {
        //获取userID
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        if (userId == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        //获取用户的详情信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        //根据前端传递过来的参数进行修改用户对应的信息
        user.setNickName(userUpdateDTO.getNickName());
        user.setSex(userUpdateDTO.getSex());
        user.setSchoolName(userUpdateDTO.getSchoolName());
        user.setMajorName(userUpdateDTO.getMajorName());
        user.setPhone(userUpdateDTO.getPhone());
        user.setEmail(userUpdateDTO.getEmail());
        user.setWechat(userUpdateDTO.getWechat());
        user.setIntroduce(userUpdateDTO.getIntroduce());
        //更新用户缓存
        userCacheManager.refreshUser(user);
        tokenService.refreshLoginUser(user.getNickName(),user.getHeadImage(),
                ThreadLocalUtil.get(Constants.USER_KEY, String.class));
        return userMapper.updateById(user);
    }

    @Override
    public int updateHeadImage(String headImage) {
        //获取userID
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        if (userId == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        //获取用户的详情信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        //根据前端传递过来的参数进行修改用户对应的信息
        user.setHeadImage(headImage);
        //更新用户缓存
        userCacheManager.refreshUser(user);
        tokenService.refreshLoginUser(user.getNickName(),user.getHeadImage(),
                ThreadLocalUtil.get(Constants.USER_KEY, String.class));
        return userMapper.updateById(user);
    }


    private void checkCode(String email, String code) {
        //先进行邮箱验证码的比对
        String emailCodeKey = getEmailCodeKey(email);
        //在redis中获取验证码
        String cacheCode = redisService.getCacheObject(emailCodeKey, String.class);
        //判断验证是否过期了
        if (StrUtil.isEmpty(cacheCode)) {
            throw new ServiceException(ResultCode.FAILED_INVALID_CODE);
        }
        //判断验证码是否错误(必须将前端的code放入括号内，因为前端传入的code可能为null)
        if (!cacheCode.equals(code)) {
            throw new ServiceException(ResultCode.FAILED_ERROR_CODE);
        }
        //验证码使用完毕后需要进行删除
        redisService.deleteObject(emailCodeKey);
    }

    /**
     * 判断邮箱是否合法
     * @param email
     * @return
     */
    public static boolean checkEmail(String email) {
        return email != null &&
                !email.isEmpty() &&
                email.matches("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");
    }

    private String getEmailCodeKey(String email) {
        return CacheConstants.EMAIL_CODE_KEY + email;
    }

    private String getCodeTimeKey(String email) {
        return CacheConstants.CODE_TIME_KEY + email;
    }
}

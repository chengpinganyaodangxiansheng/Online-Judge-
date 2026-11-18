package org.example.common.core.constants;

import lombok.Data;


public class CacheConstants {
    public static final String LOGIN_TOKEN_KEY = "logintoken:";

    //过期时间
    public static final Long EXP = 720L;


    //刷新时长
    public static final Long REFRESH_TIME = 180L;


    //存储验证码的key的前缀
    public static final String EMAIL_CODE_KEY = "e:c:";

    //当天获取验证码的次数
    public static final String CODE_TIME_KEY = "c:t:";

    // 未完赛竞赛列表的key前缀
    public final static String EXAM_UNFINISHED_LIST = "e:t:l";

    // 历史竞赛列表的key前缀
    public final static String EXAM_HISTORY_LIST = "e:h:l";

    // 竞赛详情信息的key前缀
    public final static String EXAM_DETAIL = "e:d:";

    public final static String USER_EXAM_LIST = "u:e:l:";   //用户竞赛列表

    public final static String USER_DETAIL = "u:d:";   //用户详情信息

    public final static long USER_EXP = 10;
    public static final String USER_UPLOAD_TIMES_KEY = "u:u:t";

    public static final String QUESTION_LIST = "q:l";

    public static final String QUESTION_HOST_LIST = "q:h:l";

    public static final String EXAM_QUESTION_LIST = "e:q:l:";

    public static final String USER_MESSAGE_LIST = "u:m:l:";

    public static final String MESSAGE_DETAIL = "m:d:";

    public static final String EXAM_RANK_LIST = "e:r:l:";

    public static final long DEFAULT_START = 0;

    public static final long DEFAULT_END = -1;
}

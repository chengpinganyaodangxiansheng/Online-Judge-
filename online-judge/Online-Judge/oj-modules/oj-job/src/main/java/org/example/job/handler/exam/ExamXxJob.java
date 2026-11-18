package org.example.job.handler.exam;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.example.common.core.constants.CacheConstants;
import org.example.common.core.constants.Constants;
import org.example.common.redis.service.RedisService;
import org.example.job.domain.exam.Exam;
import org.example.job.domain.message.Message;
import org.example.job.domain.message.MessageText;
import org.example.job.domain.message.vo.MessageTextVO;
import org.example.job.domain.user.UserScore;
import org.example.job.mapper.exam.ExamMapper;
import org.example.job.mapper.user.UserExamMapper;
import org.example.job.mapper.user.UserSubmitMapper;
import org.example.job.service.MessageService;
import org.example.job.service.MessageTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Component
@Slf4j
public class ExamXxJob {
    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private RedisService redisService;

    @Autowired
    private UserSubmitMapper userSubmitMapper;

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageTestService messageTestService;

    @Autowired
    private UserExamMapper userExamMapper;


    //竞赛列表重置
    @XxlJob("examListOrganizeHandler")
    public void examListOrganizeHandler() {
        log.info("**** examListOrganizeHandler ****");
        //查询未完赛的竞赛列表
        List<Exam> unFinishList = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                                .select(Exam::getExamId, Exam::getTitle, Exam::getStartTime, Exam::getEndTime)
                                .gt(Exam::getEndTime, LocalDateTime.now())
                                .eq(Exam::getStatus, Constants.TRUE)
                                .orderByDesc(Exam::getCreateTime));
        //查询后在进行刷新
        refreshCache(unFinishList,CacheConstants.EXAM_UNFINISHED_LIST);
        //查询历史竞赛
        List<Exam> historyList = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                                .select(Exam::getExamId, Exam::getTitle, Exam::getStartTime, Exam::getEndTime)
                                .le(Exam::getEndTime, LocalDateTime.now())
                                .eq(Exam::getStatus, Constants.TRUE)
                                .orderByDesc(Exam::getCreateTime));
        //查询后在进行刷新
        refreshCache(historyList,CacheConstants.EXAM_HISTORY_LIST);

    }

    @XxlJob("examResultHandler")
    //处理前一天的竞赛排名信息(每天凌晨1点进行排名处理,并将消息发送给对应到用户)
    public void examResultHandler() {
        //判断符合预期的竞赛(结束时间小于今天，大于昨天的时间)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime minusDateTime = now.minusDays(1);
        List<Exam> examList = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .select(Exam::getExamId, Exam::getTitle)
                .eq(Exam::getStatus, Constants.TRUE)
                .ge(Exam::getEndTime, minusDateTime)
                .le(Exam::getEndTime, now));
        if (CollectionUtil.isEmpty(examList)) {
            return;
        }

        //获取竞赛的Id列表使用Set防止Id重复
        Set<Long> examIdList = examList.stream().map(Exam::getExamId).collect(Collectors.toSet());
        //获取全部竞赛的用户得分列表
        List<UserScore> userScoreList = userSubmitMapper.selectUserScoreList(examIdList);
        //按照竞赛进行分组获取不同竞赛的用户得分列表   (因为在redis中，用户得分的详细信息value是通过竞赛id+指定的字符串拼接而成的key来存储的)
        Map<Long, List<UserScore>> userScoreMap = userScoreList.stream().collect(Collectors.groupingBy(UserScore::getExamId));
        //创建消息
        createMessage(examList, userScoreMap);
    }

    private void createMessage(List<Exam> examList, Map<Long, List<UserScore>> userScoreMap) {
        List<Message> messageList = new ArrayList<>();
        List<MessageText> messageTextList = new ArrayList<>();

        //遍历每一个竞赛，获取每个用户列表的得分情况
        for (Exam exam : examList) {
            Long examId = exam.getExamId();
            List<UserScore> userScoreList = userScoreMap.get(examId);
            //获取当前竞赛列表的总人数
            int totalUser = userScoreList.size();
            //排名
            int examRank = 1;
            //遍历用户得分列表，来对每个用户得分情况的消息进行处理
            for (UserScore userScore : userScoreList) {
                //题目和内容编辑
                String msgTitle =  exam.getTitle() + "——排名情况";
                String msgContent = "您所参与的竞赛：" + exam.getTitle()
                        + "，本次参与竞赛一共" + totalUser + "人， 您排名第"  + examRank + "名！";

                //对消息内容进行编辑
                MessageText messageText = new MessageText();
                //消息题目、内容和创建人
                messageText.setMessageTitle(msgTitle);
                messageText.setMessageContent(msgContent);
                messageText.setCreateBy(Constants.SYSTEM_USER_ID);
                messageTextList.add(messageText);

                //用户排名
                userScore.setExamRank(examRank);

                //对消息进行编辑
                Message message = new Message();
                //消息的发送方id、创建者和接收者id
                message.setSendId(Constants.SYSTEM_USER_ID);
                message.setCreateBy(Constants.SYSTEM_USER_ID);
                message.setRecId(userScore.getUserId());
                messageList.add(message);

                //排名数值
                examRank++;
            }
            //直接在数据库中批量更新用户的得分信息
            userExamMapper.updateUserScoreAndRank(userScoreList);


            //还需要把数据放到redis缓存当中
            //存储的结构是列表  key : e:r:l:examId  value:json{ userId , score , examRank}(nickName通过userId从用户表中查询，不能放入到缓存，因为nickname会发生改变)
            redisService.rightPushAll(getExamRankListKey(examId), userScoreList);
        }
        //对Test消息进行批量插入
        messageTestService.batchInsert(messageTextList);

        Map<String,MessageTextVO> messageTextVOMap = new HashMap<>();
        //先让消息内容进行插入数据库后，生成了对应的TextId，再把值给消息Message中,顺便处理消息详情信息的redis缓存
        for (int i = 0; i < messageTextList.size(); i++) {
            MessageText messageText = messageTextList.get(i);
            Message message = messageList.get(i);
            message.setTextId(messageText.getTextId());

            //此时还需要对redis缓存进行刷新
            MessageTextVO messageTextVO = new MessageTextVO();
            BeanUtil.copyProperties(messageText, messageTextVO);
            String msgDetailKey  = getDetailKey(messageText.getTextId());
            messageTextVOMap.put(msgDetailKey,messageTextVO);
        }
        redisService.multiSet(messageTextVOMap);

        //对消息进行批量插入
        messageService.batchInsert(messageList);

        //处理每个用户的用户消息列表的redis缓存
        Map<Long, List<Message>> userMsgMap = messageList.stream().collect(Collectors.groupingBy(Message::getRecId));//从消息列表中按照接收人的id(用户Id)进行分组获取message的列表
        //遍历map,对redis缓存的用户消息列表进行刷新
        for (Map.Entry<Long, List<Message>> entry : userMsgMap.entrySet()) {
            //通过用户Id拼接指定的字符串生成Key值
            Long userId = entry.getKey();
            String userMsgListKey = getUserMsgListKey(userId);
            //获取textId列表(对应用户的Id)
            List<Long> list = entry.getValue().stream().map(Message::getTextId).toList();
            //插入redis缓存中
            redisService.rightPushAll(userMsgListKey,list);
        }

    }

    //刷新缓存逻辑
    public void refreshCache(List<Exam> examList, String examListKey) {
        //如果数据库中也没有数据，直接进行返回
        if (CollectionUtil.isEmpty(examList)) {
            return;
        }

        //封装数据，把数据放入到redis的缓存中
        Map<String, Exam> examMap = new HashMap<>();
        //将redis中需要的examId进行获取list集合
        List<Long> examIdList = new ArrayList<>();
        for (Exam exam : examList) {
            //在Map中放入redis中真正存储信息的key和value
            examMap.put(getDetailKey(exam.getExamId()), exam);
            examIdList.add(exam.getExamId());
        }
        redisService.multiSet(examMap);
        //刷新详情缓存
        redisService.deleteObject(examListKey);
        //将列表的value尾插到redis中,也就是e:t:l or e:h:lkey的value   ->刷新列表缓存
        redisService.rightPushAll(examListKey, examIdList);
    }

    //竞赛详细信息的key
    private String getDetailKey(Long examId) {
        return CacheConstants.EXAM_DETAIL + examId;
    }

    //用户消息的key
    private String getUserMsgListKey(Long userId) {
        return CacheConstants.USER_MESSAGE_LIST + userId;
    }

    //用户详细详细消息的key
    private String getMsgDetailKey(Long textId) {
        return CacheConstants.MESSAGE_DETAIL + textId;
    }

    private String getExamRankListKey(Long examId) {
        return CacheConstants.EXAM_RANK_LIST + examId;
    }
}

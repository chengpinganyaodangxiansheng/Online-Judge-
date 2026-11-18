package org.example.friend.manager;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.github.pagehelper.PageHelper;
import org.example.common.core.constants.CacheConstants;
import org.example.common.core.domain.PageQueryDTO;
import org.example.common.redis.service.RedisService;
import org.example.friend.domain.message.vo.MessageTextVO;
import org.example.friend.mapper.message.MessageTextMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MessageCacheManager {
    @Autowired
    private RedisService redisService;

    @Autowired
    private MessageTextMapper messageTextMapper;

    //获取用户消息列表的长度
    public Long getListSize(Long userId) {
        String userMsgKey = getUserMsgListKey(userId);
        return redisService.getListSize(userMsgKey);
    }

    //获取用户消息列表Key
    private String getUserMsgListKey(Long userId) {
        return CacheConstants.USER_MESSAGE_LIST + userId;
    }

    //获取消息详情Key
    private String getMsgDetailKey(Long textId) {
        return CacheConstants.MESSAGE_DETAIL + textId;
    }

    //刷新redis缓存
    public void refreshCache(Long userId) {
        //先从数据库获取消息详情列表
        List<MessageTextVO> messageTextVOList = messageTextMapper.selectUserMsgList(userId);
        if (CollectionUtil.isEmpty(messageTextVOList)) {
            return;//数据库不存在数据
        }

        //数据库中存在数据,将用户消息列表和消息详情列表一起存入redis缓存中
        List<Long> textIdList = messageTextVOList.stream().map(MessageTextVO::getTextId).toList();//开始存储用户消息列表
        redisService.rightPushAll(getUserMsgListKey(userId), textIdList);

        //存储消息详情列表
        Map<String, MessageTextVO> messageTextVOMap = new HashMap<>();
        for (MessageTextVO messageTextVO : messageTextVOList) {
            messageTextVOMap.put(getMsgDetailKey(messageTextVO.getTextId()), messageTextVO);
        }
        redisService.multiSet(messageTextVOMap);
    }

    //获取消息详情信息
    public List<MessageTextVO> getMessageTestVO(PageQueryDTO pageQueryDTO,Long userId) {
        //分页标准
        int start = (pageQueryDTO.getPageNum() - 1) * pageQueryDTO.getPageSize();
        int end = start + pageQueryDTO.getPageSize() - 1; //下标需要 -1
        String userMsgListKey = getUserMsgListKey(userId);
        //从reids中获取当前用户的消息列表的TextId列表
        List<Long> msgTextIdList = redisService.getCacheListByRange(userMsgListKey, start, end, Long.class);
        //获取该用户每个消息的详情
        List<MessageTextVO> messageTextVOList = assembleMsgTextVOList(msgTextIdList);

        if (CollectionUtil.isEmpty(messageTextVOList)) {
            //说明redis中数据可能有问题 从数据库中查数据并且重新刷新缓存
            messageTextVOList = getMsgTextVOListByDB(pageQueryDTO, userId); //从数据库中获取数据
            refreshCache(userId);
        }

        return messageTextVOList;
    }

    //从数据库中获取信息(因为redis的缓存中存在问题或者不存咋redis缓存)
    private List<MessageTextVO> getMsgTextVOListByDB(PageQueryDTO pageQueryDTO, Long userId) {
        PageHelper.startPage(pageQueryDTO.getPageNum(), pageQueryDTO.getPageSize());
        return messageTextMapper.selectUserMsgList(userId);
    }

    //从redis缓存中获取消息详情列表
    private List<MessageTextVO> assembleMsgTextVOList(List<Long> msgTextIdList) {
        if (CollectionUtil.isEmpty(msgTextIdList)) {
            //说明redis当中没数据 从数据库中查数据并且重新刷新缓存
            return null;
        }
        //拼接redis当中key的方法 并且将拼接好的key存储到一个list中
        List<String> detailKeyList = new ArrayList<>();
        for (Long textId : msgTextIdList) {
            detailKeyList.add(getMsgDetailKey(textId));
        }
        //拼接key是为了将所有的detailkey拿到后，通过redis 批量获取消息详情列表
        List<MessageTextVO> messageTextVOList = redisService.multiGet(detailKeyList, MessageTextVO.class);
        //移除空值的消息详情
        CollUtil.removeNull(messageTextVOList);
        if (CollectionUtil.isEmpty(messageTextVOList) || messageTextVOList.size() != msgTextIdList.size()) {
            //说明redis中数据有问题 从数据库中查数据并且重新刷新缓存
            return null;
        }
        return messageTextVOList;
    }
}

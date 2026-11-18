package org.example.friend.service.user.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.example.common.core.constants.Constants;
import org.example.common.core.domain.PageQueryDTO;
import org.example.common.core.domain.TableDataInfo;
import org.example.common.core.utils.ThreadLocalUtil;
import org.example.friend.domain.exam.vo.ExamVO;
import org.example.friend.domain.message.vo.MessageTextVO;
import org.example.friend.manager.MessageCacheManager;
import org.example.friend.mapper.message.MessageTextMapper;
import org.example.friend.service.user.UserMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserMessageServiceImpl implements UserMessageService {
    @Autowired
    private MessageCacheManager messageCacheManager;

    @Autowired
    private MessageTextMapper messageTextMapper;

    @Override
    public TableDataInfo list(PageQueryDTO pageQueryDTO) {
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        //从redis中获取缓存数据,返回值是获取的数据总量
        Long total = messageCacheManager.getListSize(userId);
        List<MessageTextVO> messageTextVOList;
        //如果数据不存在
        if (total == null || total <= 0) {
            //先从数据库进行查询
            PageHelper.startPage(pageQueryDTO.getPageNum(), pageQueryDTO.getPageSize());
            messageTextVOList = messageTextMapper.selectUserMsgList(userId);
            //再把数据进行刷新，刷新的过程中也会将数据放入redis的缓存中
            messageCacheManager.refreshCache(userId);
            //此时redis缓存中没有数据，total就是不存在的所以需要重新获取
            total = new PageInfo<>(messageTextVOList).getTotal();
        }else {
            //如果数据存在，直接从redis缓存中获取信息
            messageTextVOList = messageCacheManager.getMessageTestVO(pageQueryDTO,userId);
            //在getExamVOList的方法中会进行refreshCache方法，此时可能total在redis获取的是异常情况，之前的total就不准确
            //因为total是异常时，会重新进入数据库进行查询数据，并且更新缓存
            total = messageCacheManager.getListSize(userId);
        }

        if (CollectionUtil.isEmpty(messageTextVOList)) {
            return TableDataInfo.empty();
        }

        return TableDataInfo.success(messageTextVOList,total);
    }
}

package com.weave.recommend.service.impl;

import com.weave.recommend.mapper.UserActionMapper;
import com.weave.recommend.service.ActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.weave.recommend.model.dto.ActionDto;
import com.weave.recommend.model.entity.UserAction;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionServiceImpl implements ActionService {

    private final UserActionMapper userActionMapper;

    @Override
    public void addRecord(ActionDto dto) {
        UserAction action =UserAction.builder()
                .userId(dto.userId())
                .postId(dto.postId())
                .type(dto.type())
                .createdTime(LocalDateTime.now())
                .build();
        try{
            userActionMapper.insert(action);
        } catch (Exception e) {
            log.error("添加用户行为记录失败: userId={}, postId={}, type={}", dto.userId(), dto.postId(), dto.type(), e);
        }
    }

    @Override
    public void deleteRecord(ActionDto dto) {
        userActionMapper.deleteByUserIdAndPostId(dto.userId(), dto.postId());
        log.debug("删除用户行为记录: userId={}, postId={}", dto.userId(), dto.postId());
    }
}
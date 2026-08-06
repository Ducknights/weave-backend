package com.weave.user.service.impl;

import com.weave.model.model.dto.PostDetailVo;
import com.weave.redis.util.RedisUtil;
import com.weave.user.exception.BusinessException;
import com.weave.user.model.constant.CacheSpec;
import com.weave.user.model.dto.ActionDto;
import com.weave.user.model.eunms.UserApiStatus;
import com.weave.user.model.dto.ActionRecordsPageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.redis.constant.CacheKey;
import com.weave.user.feign.PostFeignClient;
import com.weave.user.model.entity.UserActions;
import com.weave.user.mapper.ActionMapper;
import com.weave.user.model.eunms.ActionType;
import com.weave.user.service.ActionService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class ActionServiceImpl implements ActionService {

    private final RedisUtil redisUtil;
    private final ActionMapper actionMapper;
    private final RedissonClient redissonClient;
    private final PostFeignClient postFeignClient;

    @Override
    public void addRecord(ActionDto dto) {
        UserActions userActions = new UserActions(null, dto.userId(), dto.targetId(), dto.type(), LocalDateTime.now());
        try {
            // 添加记录
            actionMapper.insert(userActions);
            // 删除缓存
            redisUtil.delete(buildCacheKey(dto));
        } catch (DuplicateKeyException e) {
            log.error("重复添加记录", e);
        } catch (Exception e) {
            log.error("添加记录时发生错误", e);
        }
    }

    @Override
    public void deleteRecord(ActionDto dto) {
        try {
            // 删除记录
            actionMapper.deleteRecord(dto);
            // 删除缓存
            redisUtil.delete(buildCacheKey(dto));
        } catch (Exception e) {
            throw new BusinessException(UserApiStatus.SYSTEM_ERROR);
        }
    }

    @Override
    public ActionRecordsPageDto getRecord(ActionDto dto, Long cursorId, Integer limit) {
        List<UserActions> userActions = actionMapper.getRecord(dto, cursorId, limit);
        if (userActions.isEmpty()) {
            return ActionRecordsPageDto.builder().total(0).build();
        }
        Set<Long> postIds = userActions.stream().map(UserActions::getTargetId).collect(Collectors.toSet());
        // 通过 Feign 批量获取帖子详情
        List<PostDetailVo> postDetails = postFeignClient.getPostsByIds(postIds);
        // 构建映射，确保顺序
        Map<Long, PostDetailVo> postMap = postDetails.stream().collect(Collectors.toMap(PostDetailVo::getId, Function.identity()));
        if (postDetails.isEmpty()){
            return ActionRecordsPageDto.builder().total(0).build();
        }
        // 构建结果
        return ActionRecordsPageDto.builder()
                .actions(new ArrayList<>(postMap.values()))
                .total(postMap.size())
                .cursorId(userActions.get(userActions.size() - 1).getId())
                .build();
    }

    private String buildCacheKey(ActionDto dto) {
        return switch (dto.type()) {
            case LIKE -> CacheKey.buildCacheKey(CacheSpec.ActionCache.USER_LIKED_POSTS, dto.userId());
            case COLLECT -> CacheKey.buildCacheKey(CacheSpec.ActionCache.USER_COLLECTED_POSTS, dto.userId());
            case VIEW -> CacheKey.buildCacheKey(CacheSpec.ActionCache.USER_VIEWED_POSTS, dto.userId());
        };
    }

    @Override
    public void cacheUserAction(Long userId) {
        String lockKey = CacheKey.buildCacheKey(CacheSpec.ActionCache.USER_ACTION_CACHE_LOCK, userId);
        RLock lock = redissonClient.getLock(lockKey);
        // 缓存用户操作记录
        for (ActionType action : ActionType.values()) {
            ActionDto dto = new ActionDto(userId,null, action);
            Set<Long> postIds = actionMapper.getAllTargetIdsByUserAndType(dto);
            if (postIds != null && !postIds.isEmpty()) {
                String key = buildCacheKey(dto);
                try {
                    redisUtil.addListToSet(key, postIds, CacheSpec.ActionCache.USER_ACTION_CACHE_TTL);
                } catch (Exception e) {
                    log.error("缓存用户操作记录失败", e);
                }
            }
        }
    }
}

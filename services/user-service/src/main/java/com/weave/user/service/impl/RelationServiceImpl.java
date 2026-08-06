package com.weave.user.service.impl;

import com.weave.model.model.dto.UserBriefDto;
import com.weave.redis.util.RedisUtil;
import com.weave.user.mapper.RelationMapper;
import com.weave.user.model.dto.RelationRecordsPageDto;
import com.weave.user.service.RelationService;
import com.weave.user.service.UserInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.redis.constant.CacheKey;
import com.weave.user.model.dto.RelationDto;
import com.weave.user.model.entity.UserRelations;
import com.weave.user.model.eunms.RelationType;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class RelationServiceImpl implements RelationService {

    private final RedisUtil redisUtil;
    private final RelationMapper relationMapper;
    private final UserInfoService userInfoService;

    /**
     * 添加/修改记录 —— userId 与 targetId 之间只能存在一种关系
     */
    @Override
    public void addRecord(RelationDto dto) {
        redisUtil.delete(buildCacheKey(dto));
        relationMapper.upsertRecord(dto);
    }

    /**
     * 删除记录
     */
    @Override
    public void deleteRecord(RelationDto dto) {
        redisUtil.delete(buildCacheKey(dto));
        relationMapper.deleteRecord(dto);
    }

    /**
     * 游标分页查询用户关系列表
     */
    @Override
    public RelationRecordsPageDto getUserBriefByRelation(RelationDto dto, Long cursorId, Integer limit) {
        List<UserRelations> relations = relationMapper.getRecordCursor(dto, cursorId, limit);
        if (relations.isEmpty()) {
            return RelationRecordsPageDto.builder().total(0).build();
        }
        Set<Long> targetIds = relations.stream().map(UserRelations::getTargetId).collect(Collectors.toSet());
        Map<Long, UserBriefDto> userMap = userInfoService.getUserInfosByIds(targetIds);
        List<UserBriefDto> records = new ArrayList<>(userMap.values());
        Long nextCursor = relations.get(relations.size() - 1).getId();
        return RelationRecordsPageDto.builder()
                .records(records)
                .total(records.size())
                .cursorId(nextCursor)
                .build();
    }

    /**
     * 缓存用户关系记录
     */
    @Override
    public void cacheUserRelation(Long userId) {
        for (RelationType type : RelationType.values()) {
            RelationDto dto = new RelationDto(userId, null, type);
            List<Long> targetIds = relationMapper.getAllTargetIdsByUserAndType(dto);
            if (targetIds != null && !targetIds.isEmpty()) {
                try {
                    redisUtil.addListToSet(buildCacheKey(dto), targetIds, Duration.ofMinutes(130));
                } catch (Exception e) {
                    log.error("缓存用户关系记录失败: userId={}, type={}", userId, type, e);
                }
            }
        }
    }

    @Override
    public Set<Long> getMutedAndBlockedTargetIds(Long userId) {
        Set<Long> result = new HashSet<>();
        for (RelationType type : List.of(RelationType.MUTE, RelationType.BLOCK)) {
            RelationDto dto = new RelationDto(userId, null, type);
            String cacheKey = buildCacheKey(dto);
            Set<String> cached = redisUtil.getSetMembers(cacheKey);
            if (!cached.isEmpty()) {
                cached.stream().map(Long::valueOf).forEach(result::add);
            } else {
                List<Long> dbIds = relationMapper.getAllTargetIdsByUserAndType(dto);
                result.addAll(dbIds);
            }
        }
        return result;
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(RelationDto dto) {
        return switch (dto.type()) {
            case FOLLOW -> CacheKey.buildCacheKey(CacheKey.USER_FOLLOWERS, dto.userId());
            case MUTE -> CacheKey.buildCacheKey(CacheKey.USER_MUTED_USERS, dto.userId());
            case BLOCK -> CacheKey.buildCacheKey(CacheKey.USER_BLOCKED_USERS, dto.userId());
        };
    }
}
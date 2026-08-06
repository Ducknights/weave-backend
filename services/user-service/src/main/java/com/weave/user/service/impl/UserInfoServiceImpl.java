package com.weave.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weave.redis.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.redis.annotation.RedisCacheable;
import com.weave.redis.constant.CacheKey;
import com.weave.model.model.dto.AuthUserDto;
import com.weave.model.model.dto.PostDetailVo;
import com.weave.model.model.dto.UserBriefDto;
import com.weave.user.feign.PostFeignClient;
import com.weave.user.mapper.UserInfoMapper;
import com.weave.model.model.ApiResult;
import com.weave.user.model.dto.UpdateUserInfoDto;
import com.weave.user.model.entity.UserInfo;
import com.weave.user.model.vo.UserInfoVo;
import com.weave.user.service.UserInfoService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

    private final UserInfoMapper userInfoMapper;
    private final RedisUtil redisUtil;
    private final PostFeignClient postFeignClient;

    /**
     * 根据用户ID集合批量获取用户信息
     * @param ids 用户ID集合
     * @return 返回用户ID与用户信息的映射Map
     */
    @Override
    public Map<Long, UserBriefDto> getUserInfosByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, UserBriefDto> result = new HashMap<>();
        Set<Long> needQueryIds = new HashSet<>();

        // 从缓存中获取用户信息
        ids.forEach(id -> {
            // 尝试从redis中获取用户信息
            String key = CacheKey.buildCacheKey(CacheKey.USER_BRIEF_INFO, id);
            UserBriefDto cachedUser = redisUtil.get(key, UserBriefDto.class);
            // 缓存命中，直接使用缓存中的数据
            if (cachedUser != null) {
                result.put(id, cachedUser);
            } else {
                // 缓存未命中，需要从数据库中查询的id
                needQueryIds.add(id);
            }
        });

        if (!needQueryIds.isEmpty()) {
            // 从数据库中查询
            List<UserInfo> userList = this.listByIds(needQueryIds);
            
            // 记录未找到的用户ID
            Set<Long> notFoundIds = new HashSet<>(needQueryIds);
            
            // 处理查询到的用户
            if (userList != null && !userList.isEmpty()) {
                for (UserInfo user : userList) {
                    // 创建用户简要信息对象并缓存
                    UserBriefDto userBriefDto = new UserBriefDto(user.getId(), user.getName(), user.getAvatar());
                    String key = CacheKey.buildCacheKey(CacheKey.USER_BRIEF_INFO, user.getId());
                    redisUtil.set(key, userBriefDto, Duration.ofMinutes(120));
                    // 将用户信息添加到结果集中
                    result.put(user.getId(), userBriefDto);
                    notFoundIds.remove(user.getId());
                }
            }
            
            // 处理未找到的用户，构建空对象并缓存
            for (Long id : notFoundIds) {
                UserBriefDto emptyUser = UserBriefDto.buildEmpty(id);
                String key = CacheKey.buildCacheKey(CacheKey.USER_BRIEF_INFO, id);
                redisUtil.set(key, emptyUser, Duration.ofMinutes(120));
                result.put(id, emptyUser);
            }
        }
        return result;
    }

    /**
     * 创建用户
     * @param userDto 用户信息
     * @return 返回创建的用户信息
     */
    @Override
    @CacheEvict(value = CacheKey.USER_BRIEF_INFO, key = "#userDto.id")
    public UserInfo createUser(AuthUserDto userDto) {
        UserInfo user =UserInfo.builder()
                .id(userDto.id())
                .name("用户"+userDto.id())
                .email(userDto.email())
                .build();
        userInfoMapper.insert(user);
        return user;
    }

    /**
     * 根据用户ID获取用户信息
     * @param id 用户ID
     * @return 返回用户信息
     */
    @Override
    @RedisCacheable(value = CacheKey.USER_BRIEF_INFO, key = "#id")
    public UserBriefDto getUserBriefDtoById(Long id) {
        return userInfoMapper.selectUserBriefDtoById(id);
    }

    @Override
    public UserInfoVo getUserInfoDtoById(Long id) {
        UserInfoVo vo = userInfoMapper.selectUserInfoById(id);
        ResponseEntity<ApiResult<List<PostDetailVo>>> postsResponse = postFeignClient.getPostsByUser(id);
        List<PostDetailVo> posts = Objects.requireNonNull(postsResponse.getBody()).data();
        return UserInfoVo.builder()
                .id(vo.getId())
                .name(vo.getName())
                .avatar(vo.getAvatar())
                .gender(vo.getGender())
                .motto(vo.getMotto())
                .fansCont(vo.getFansCont())
                .followCont(vo.getFollowCont())
                .postCont(posts != null ? posts.size() : 0)
                .build();
    }

    /**
     * 更新用户信息
     * @param user 用户信息
     * @return 返回更新后的用户信息
     */
    @Override
    @Transactional
    @CacheEvict(value = CacheKey.USER_BRIEF_INFO, key = "#user.id")
    public UpdateUserInfoDto updateUser(UpdateUserInfoDto user) {
        UserInfo userInfo = UserInfo.builder()
                .id(user.getId())
                .name(user.getName())
                .avatar(user.getAvatar())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .address(user.getAddress())
                .motto(user.getMotto())
                .build();
        if(userInfoMapper.updateInfo(userInfo) > 0){
            return user;
        }else {
            throw new RuntimeException("用户信息更新失败");
        }
    }
}
package com.weave.club.service.imp;

import lombok.extern.log4j.Log4j2;
import com.weave.club.exception.BusinessException;
import com.weave.club.model.entity.Activity;
import com.weave.club.mapper.ActivityMapper;
import com.weave.club.model.vo.ActivityCardVo;
import com.weave.club.service.ActivityService;
import com.weave.redis.constant.CacheKey;
import com.weave.club.model.enums.ClubApiStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;


import java.time.LocalDate;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class ActivityServiceImp implements ActivityService {

    private final ActivityMapper activityMapper;

    @Override
    public Activity creatActivity(Activity activity) {
        if(activityMapper.insert(activity)>0){
            return activity;
        }
        return null;
    }

    @Override
    @CachePut(value = CacheKey.ACTIVITY, key ="'activityId:'+ #activityId")
    public void deleteActivity(Integer activityId) {
        try {
            activityMapper.deleteById(activityId);
        } catch (Exception e) {
            log.error("删除活动失败，参数： {}", activityId, e);
            throw new RuntimeException("删除活动失败");
        }
    }

    @Override
    @CacheEvict(value = CacheKey.ACTIVITY, key ="'activityId:'+ #activity.id")
    public Activity updateActivity(Activity activity) {
        return activityMapper.updateActivity(activity);
    }

    @Override
    @Cacheable(value = CacheKey.ACTIVITY, key ="'activities:'+ #startDate +':' + #endDate")
    public List<ActivityCardVo> queryActivityByDate(LocalDate startDate, LocalDate endDate) {
        log.info("从数据库查询活动，参数： {} to {}", startDate, endDate);
        return activityMapper.queryActivity(startDate, endDate);
    }

    @Cacheable(value = CacheKey.ACTIVITY, key ="'activityId:'+ #activityId ")
    @Override
    public Activity queryActivityById(Integer activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) throw new BusinessException(ClubApiStatus.NOT_FOUND);
        return activity;
    }

    @Override
    public List<Activity> getActivitiesByClubId(Integer clubId) {
        return activityMapper.getActivitiesByClubId(clubId);
    }
}

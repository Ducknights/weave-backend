package com.weave.club.controller;


import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.weave.model.model.ApiResult;
import com.weave.club.model.entity.Activity;
import com.weave.club.model.enums.ClubApiStatus;
import com.weave.club.model.vo.ActivityCardVo;
import com.weave.club.service.ActivityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/club/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * 创建活动
     * @param activity 活动实体
     * @return 响应结果，包含创建的活动信息
     */
    @PostMapping()
    public ResponseEntity<ApiResult<Activity>> creatActivity(@Nonnull @RequestBody Activity activity) {
        Activity newActivity = activityService.creatActivity(activity);
        return ResponseEntity.ok()
                .body(ClubApiStatus.POST_SUCCESS.response(newActivity));
    }

    /**
     * 删除活动
     * @param ActivityId 活动ID
     * @return 响应结果
     */
    @DeleteMapping()
    public ResponseEntity<ApiResult<Void>> deleteActivity(@Nonnull @RequestBody Integer ActivityId) {
        activityService.deleteActivity(ActivityId);
        return ResponseEntity.status(ClubApiStatus.DELETE_SUCCESS.getCode())
                .body(ClubApiStatus.DELETE_SUCCESS.response());
    }

    /**
     * 更新活动信息
     * @param activity 活动实体
     * @return 响应结果，包含更新后的活动信息
     */
    @PutMapping()
    public ResponseEntity<ApiResult<Activity>> updateActivity(@Nonnull @RequestBody Activity activity) {
        final Activity newActivity = activityService.updateActivity(activity);
        return ResponseEntity.status(ClubApiStatus.PUT_SUCCESS.getCode())
                .body(ClubApiStatus.PUT_SUCCESS.response(newActivity));
    }

    /**
     * 根据时间范围查询活动
     * @param startDate 开始日期时间
     * @param endDate 结束日期时间
     * @return 响应结果，包含符合条件的活动列表
     */
    @GetMapping("/week")
    public ResponseEntity<ApiResult<List<ActivityCardVo>>> getActivity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        // 转换为LocalDate，只保留日期部分
        LocalDate start = startDate.toLocalDate();
        LocalDate end = endDate.toLocalDate();

        if (start.isAfter(end)){
            throw new IllegalArgumentException("开始日期必须在结束日期之前");
        }
        final List<ActivityCardVo> activities = activityService.queryActivityByDate(start, end);
        log.info("查询到{}条活动", activities.size());
        return ResponseEntity.status(ClubApiStatus.GET_SUCCESS.getCode())
                .body(ClubApiStatus.GET_SUCCESS.response(activities));
    }

    /**
     * 根据ID获取活动信息
     * @param ActivityId 活动ID
     * @return 响应结果，包含指定ID的活动信息
     */
    @GetMapping("{ActivityId}")
    public ResponseEntity<ApiResult<Activity>> getActivityById(@PathVariable Integer ActivityId) {
        final Activity activity = activityService.queryActivityById(ActivityId);
        return ResponseEntity.status(ClubApiStatus.GET_SUCCESS.getCode())
                .body(ClubApiStatus.GET_SUCCESS.response(activity));
    }

    @GetMapping("/club/{clubId}")
    public ResponseEntity<ApiResult<List<Activity>>> getActivitiesByClubId(@PathVariable Integer clubId) {
        final List<Activity> activities = activityService.getActivitiesByClubId(clubId);
        return ResponseEntity.status(ClubApiStatus.GET_SUCCESS.getCode())
                .body(ClubApiStatus.GET_SUCCESS.response(activities));
    }
}

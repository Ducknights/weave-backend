package com.weave.draft.controller;

import com.weave.draft.model.dto.DraftDto;
import com.weave.model.model.ApiResult;
import com.weave.draft.model.dto.ReviewDto;
import com.weave.draft.model.enums.DraftApiStatus;
import com.weave.draft.model.vo.DraftVo;
import com.weave.draft.service.DraftService;
import com.weave.security.util.SecurityUtils;
import jakarta.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 草稿与审核流程控制器
 */
@Log4j2
@RestController
@RequestMapping("/api/draft")
public class DraftController {

    @Resource
    private DraftService draftService;

    /**
     * 保存草稿
     * POST /api/draft
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'OFFICER')")
    public ResponseEntity<ApiResult<Long>> saveDraft(@RequestBody DraftDto draftDto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long draftId = draftService.saveDraft(userId, draftDto);
        return ResponseEntity.ok(DraftApiStatus.SAVE_DRAFT_SUCCESS.response(draftId));
    }

    /**
     * 更新草稿
     * PUT /api/draft/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'OFFICER')")
    public ResponseEntity<ApiResult<Void>> updateDraft(@PathVariable Long id, @RequestBody DraftDto draftDto) {
        Long userId = SecurityUtils.getCurrentUserId();
        draftService.updateDraft(id, userId, draftDto);
        return ResponseEntity.ok(DraftApiStatus.UPDATE_SUCCESS.response());
    }

    /**
     * 删除草稿
     * DELETE /api/draft/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'OFFICER')")
    public ResponseEntity<ApiResult<Void>> deleteDraft(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        draftService.deleteDraft(id, userId);
        return ResponseEntity.ok(DraftApiStatus.DELETE_SUCCESS.response());
    }

    /**
     * 提交审核（草稿 -> 审核中）
     * PUT /api/draft/{id}/submit
     */
    @PutMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('USER', 'OFFICER')")
    public ResponseEntity<ApiResult<Void>> submitForReview(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        draftService.submitForReview(id, userId);
        return ResponseEntity.ok(DraftApiStatus.SUBMIT_SUCCESS.response());
    }

    /**
     * 审核通过（审核中 -> 审核通过，并发布）
     * PUT /api/draft/{id}/approve
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<ApiResult<Void>> approve(@PathVariable Long id, @RequestBody(required = false) ReviewDto reviewDto) {
        Long reviewerId = SecurityUtils.getCurrentUserId();
        String remark = reviewDto == null ? null : reviewDto.getRemark();
        draftService.approve(id, reviewerId, remark);
        return ResponseEntity.ok(DraftApiStatus.APPROVE_SUCCESS.response());
    }

    /**
     * 审核驳回（审核中 -> 审核驳回）
     * PUT /api/draft/{id}/reject
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<ApiResult<Void>> reject(@PathVariable Long id, @RequestBody(required = false) ReviewDto reviewDto) {
        Long reviewerId = SecurityUtils.getCurrentUserId();
        String remark = reviewDto == null ? null : reviewDto.getRemark();
        draftService.reject(id, reviewerId, remark);
        return ResponseEntity.ok(DraftApiStatus.REJECT_SUCCESS.response());
    }

    /**
     * 获取当前用户的草稿详情
     * GET /api/draft/{id}
     */
    @GetMapping()
    @PreAuthorize("hasAnyRole('USER', 'OFFICER')")
    public ResponseEntity<ApiResult<DraftVo>> getDraftDetail() {
        Long userId = SecurityUtils.getCurrentUserId();
        DraftVo vo = draftService.getDraftDetail(userId);
        return ResponseEntity.ok(DraftApiStatus.SUCCESS.response(vo));
    }

    /**
     * 获取所有待审核的草稿列表（审核员/管理员）
     * GET /api/draft/review
     */
    @GetMapping("/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public ResponseEntity<ApiResult<List<DraftVo>>> getAllPendingDrafts() {
        List<DraftVo> vos = draftService.getAllPendingDrafts();
        return ResponseEntity.ok(DraftApiStatus.SUCCESS.response(vos));
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("草稿服务运行正常");
    }
}

/**
 * 成员管理控制器
 * 提供社团成员的增删改查等基本操作
 */
package com.weave.club.controller;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import com.weave.model.model.ApiResult;
import com.weave.club.model.entity.Member;
import com.weave.club.model.enums.ClubApiStatus;
import com.weave.club.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/club/{clubId}/members")
public class MemberController {

    @Resource
    private MemberService memberService;

    /**
     * 删除成员
     * @param memberId 成员ID
     * @return 响应结果
     */
    @DeleteMapping()
    public ResponseEntity<ApiResult<Void>> deleteMember(@Nonnull @RequestBody Integer memberId) {
        memberService.deleteMember(memberId);
        return ResponseEntity.status(ClubApiStatus.DELETE_SUCCESS.getCode())
                .body(ClubApiStatus.DELETE_SUCCESS.response());
    }

    /**
     * 更新成员信息
     * @param member 成员实体
     * @return 响应结果，包含更新后的成员信息
     */
    @PutMapping()
    public ResponseEntity<ApiResult<Member>> updateMember(@Nonnull @RequestBody Member member) {
        final Member newMember = memberService.updateMember(member);
        return ResponseEntity.status(ClubApiStatus.PUT_SUCCESS.getCode())
                .body(ClubApiStatus.PUT_SUCCESS.response(newMember));
    }

    /**
     * 根据社团ID获取成员列表
     * @param clubId 社团ID
     * @return 响应结果，包含指定社团的所有成员列表
     */
    @GetMapping
    public ResponseEntity<ApiResult<List<Member>>> getMembersByClubId(@PathVariable Integer clubId) {
        final List<Member> members = memberService.getMembersByClubId(clubId);
        return ResponseEntity.status(ClubApiStatus.GET_SUCCESS.getCode())
                .body(ClubApiStatus.GET_SUCCESS.response(members));
    }

    /**
     * 根据ID获取成员信息
     * @param memberId 成员ID
     * @return 响应结果，包含指定ID的成员信息
     */
    @GetMapping("/{memberId}")
    public ResponseEntity<ApiResult<Member>> getMemberById(@PathVariable Integer memberId) {
        final Member member = memberService.getMemberById(memberId);
        return ResponseEntity.status(ClubApiStatus.GET_SUCCESS.getCode())
                .body(ClubApiStatus.GET_SUCCESS.response(member));
    }
}
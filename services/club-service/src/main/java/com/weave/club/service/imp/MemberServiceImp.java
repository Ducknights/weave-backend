package com.weave.club.service.imp;

import com.weave.club.model.entity.Member;
import com.weave.club.mapper.MemberMapper;
import com.weave.club.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberServiceImp implements MemberService {

    private final MemberMapper memberMapper;

    @Override
    public void createMember(Member member) {
        memberMapper.insert(member);
    }

    @Override
    public void deleteMember(Integer memberId) {
        memberMapper.deleteMember(memberId);
    }

    @Override
    public Member updateMember(Member member) {
        return memberMapper.updateMember(member);
    }

    @Override
    public List<Member> queryMembers() {
        return memberMapper.queryMembers();
    }

    @Override
    public Member getMemberById(Integer memberId) {
        return memberMapper.getMemberById(memberId);
    }

    @Override
    public List<Member> getMembersByClubId(Integer clubId) {
        return memberMapper.getMembersByClubId(clubId);
    }

    @Override
    public List<Member> getMembersByUserId(Integer userId) {
        return memberMapper.getMembersByUserId(userId);
    }
}
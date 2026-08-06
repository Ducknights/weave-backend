package com.weave.club.service.imp;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import com.weave.model.model.dto.ClubBriefDto;
import com.weave.club.model.entity.Club;
import com.weave.club.model.entity.Member;
import com.weave.club.model.enums.ClubRole;
import com.weave.club.model.enums.MemberStatus;
import com.weave.club.model.vo.ClubCardVo;
import com.weave.club.mapper.ClubMapper;
import com.weave.club.service.ClubService;
import com.weave.club.service.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClubServiceImp extends ServiceImpl<ClubMapper, Club> implements ClubService {

    private final ClubMapper clubMapper;
    private final MemberService memberService;

    @Override
    public Club createClub(Club club) {
        if (clubMapper.insert(club)>0){
            return club;
        };
        return null;
    }

    @Override
    public void deleteClub(Integer clubId) {
        clubMapper.deleteById(clubId);
    }

    @Override
    public Club updateClub(Club club) {
        LambdaUpdateWrapper<Club> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Club::getId,club.getId())
                .set(Club::getName,club.getName())
                .set(Club::getDescription,club.getDescription());
        if(clubMapper.update(updateWrapper)>0){
            return club;
        };
        return null;
    }

    @Override
    public List<ClubCardVo> queryClubs() {
        return clubMapper.queryClubs();
    }

    @Override
    public ClubCardVo getClubById(Integer clubId) {
        return clubMapper.getClubVoById(clubId);
    }

    @Override
    @Transactional
    public void joinClub(Integer clubId, Long userId) {
        // 构建成员对象
        Member member = Member.builder()
                .userId(userId.intValue())
                .clubId(clubId)
                .role(ClubRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();
        // 创建成员记录
        memberService.createMember(member);
    }

    @Override
    public Map<Long, ClubBriefDto> batchClubsById(List<Integer> clubIds) {
        List<Club> clubs = listByIds(clubIds);
        return clubs.stream()
                .collect(Collectors.toMap(
                        club -> club.getId().longValue(),
                        club -> ClubBriefDto.builder()
                                .id(club.getId().longValue())
                                .name(club.getName())
                                .description(club.getDescription())
                                .build()
                ));
    }
}

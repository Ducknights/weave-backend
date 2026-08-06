package com.weave.search.service.impl;

import com.weave.search.service.ConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.model.model.dto.PostDetailVo;
import com.weave.search.feign.PostFeignClient;
import com.weave.search.model.dto.SearchResultDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConversionServiceImpl implements ConversionService {

    private final PostFeignClient postFeignClient;

    @Override
    public List<PostDetailVo> convertToPostDetailVo(List<SearchResultDto> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        // 1. 提取帖子ID列表
        List<Long> postIds = results.stream()
                .map(SearchResultDto::getId)
                .toList();

        // 2. 批量获取帖子信息
        List<PostDetailVo> postList = postFeignClient.getPostsByIds(postIds);
        if (postList == null || postList.isEmpty()) {
            return List.of();
        }

        //构造map
        Map<Long, PostDetailVo> postMap = postList.stream()
                .collect(Collectors.toMap(PostDetailVo::getId, post -> post));

        // 3. 按照搜索结果顺序组装，并设置搜索分数
        List<PostDetailVo> postVoList = new ArrayList<>();
        for (SearchResultDto result : results) {
            PostDetailVo post = postMap.get(result.getId());
            if (post != null) {
                post.setScore(result.getScore());
                postVoList.add(post);
            }
        }
        return postVoList;
    }
}

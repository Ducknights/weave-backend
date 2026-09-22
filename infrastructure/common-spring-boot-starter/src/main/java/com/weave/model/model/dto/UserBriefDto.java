package com.weave.model.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserBriefDto implements Serializable {
    private Long id;
    private String name;
    private String avatar;

    /**
     * 构建一个空的UserBriefDto对象
     */
    public static UserBriefDto buildEmpty(Long id) {
        return new UserBriefDto(id, "", "");
    }
}
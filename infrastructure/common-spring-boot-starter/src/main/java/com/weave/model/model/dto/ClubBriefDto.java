package com.weave.model.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubBriefDto {
    private Long id;
    private String name;
    private String description;

    public static ClubBriefDto buildEmpty(Long id){
        return new ClubBriefDto(id, "", "");
    }
}

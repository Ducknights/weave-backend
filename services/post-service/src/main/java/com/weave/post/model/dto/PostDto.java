package com.weave.post.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostDto {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long clubId;
    @NonNull
    private String title;
    private String content;
    private List<String> coverImage;
}

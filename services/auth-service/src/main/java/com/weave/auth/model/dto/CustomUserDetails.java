package com.weave.auth.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomUserDetails {

    private Long userId;

    private List<String> roles;

    private List<String> authorities;

    @JsonIgnore
    private String username;

    @JsonIgnore
    private String password;
}

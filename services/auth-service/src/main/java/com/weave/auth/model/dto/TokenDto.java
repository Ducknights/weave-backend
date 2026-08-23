package com.weave.auth.model.dto;

import lombok.experimental.FieldNameConstants;

@FieldNameConstants
public record TokenDto(
        String token_type,
        String access_token,
        Integer access_token_expires_in
) {

    public TokenDto(String access, Integer accessExpire) {
        this("Bearer", access, accessExpire);
    }
}

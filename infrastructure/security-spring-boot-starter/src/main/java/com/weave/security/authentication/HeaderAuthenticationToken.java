package com.weave.security.authentication;

import com.weave.security.model.CustomUserDetails;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;

@Getter
public class HeaderAuthenticationToken extends AbstractAuthenticationToken {

    private final CustomUserDetails userDetails;

    public HeaderAuthenticationToken(CustomUserDetails userDetails) {
        super(userDetails.getAuthorities());
        this.userDetails = userDetails;
        // 设置认证状态为true
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userDetails;
    }

    public Long getUserId() {
        return userDetails != null ? userDetails.getUserId() : null;
    }

    public String getUsername() {
        return userDetails != null ? userDetails.getUsername() : null;
    }
}

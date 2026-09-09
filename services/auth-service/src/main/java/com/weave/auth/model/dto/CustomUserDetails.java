package com.weave.auth.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomUserDetails implements UserDetails {

    private Long userId;

    @JsonIgnore
    private String username;

    @JsonIgnore
    private String password;


    private List<String> roles;

    private List<String> authorities;

    @JsonIgnore
    private String rolesStr;

    @JsonIgnore
    private String authoritiesStr;

    // 获取 roles，如果为空则从 rolesStr 转换
    public List<String> getRoles() {
        if (roles == null && rolesStr != null) {
            roles = parseStringToList(rolesStr);
        }
        return roles != null ? roles : Collections.emptyList();
    }

    // 获取 authorities，如果为空则从 authoritiesStr 转换
    @JsonProperty("authorities")
    public List<String> getAuthoritiesForJson() {
        return getAuthoritiesList();
    }
    private List<String> getAuthoritiesList() {
        if (authorities == null && authoritiesStr != null) {
            authorities = parseStringToList(authoritiesStr);
        }
        return authorities != null ? authorities : Collections.emptyList();
    }

    private List<String> parseStringToList(String str) {
        if (str == null || str.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(str.split(","));
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> roleAuthorities = roles != null
                ? roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList())
                : Collections.emptyList();
        List<GrantedAuthority> permissionAuthorities = getAuthoritiesList().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return Stream.concat(roleAuthorities.stream(), permissionAuthorities.stream())
                .collect(Collectors.toList());
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return password;
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        return username;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return true;
    }
}

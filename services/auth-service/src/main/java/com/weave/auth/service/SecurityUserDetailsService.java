package com.weave.auth.service;

import com.weave.auth.mapper.AuthMapper;
import lombok.RequiredArgsConstructor;
import com.weave.model.model.dto.AuthUserDto;
import com.weave.auth.model.dto.CustomUserDetails;
import com.weave.auth.model.dto.UserAuthDto;
import com.weave.auth.feign.UserFeignClient;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SecurityUserDetailsService implements UserDetailsManager {

    private final AuthMapper authMapper;
    private final UserFeignClient userFeignClient;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        CustomUserDetails userDetails = authMapper.selectUserDetailsByEmail(username);
        if (userDetails == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return userDetails;
    }

    @Override
    public void createUser(UserDetails user) {
        UserAuthDto userAuthDto = new UserAuthDto();
        userAuthDto.setEmail(user.getUsername());
        userAuthDto.setPassword(user.getPassword());
        // 插入用户信息
        authMapper.insert(userAuthDto);
        // 插入用户角色
        authMapper.insertUserRole(userAuthDto.getId());
        // 调用用户服务插入用户信息
        userFeignClient.createUser(new AuthUserDto(userAuthDto.getId(), userAuthDto.getEmail()));
    }

    @Override
    public void updateUser(UserDetails user) {

    }

    @Override
    public void deleteUser(String username) {

    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {

    }

    @Override
    public boolean userExists(String username) {
        return false;
    }

}

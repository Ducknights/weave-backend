package com.weave.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weave.auth.model.dto.CustomUserDetails;
import com.weave.auth.model.dto.UserAuthDto;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthMapper extends BaseMapper<UserAuthDto> {

    @Select("SELECT id,email,password FROM users WHERE email = #{email}")
    // 根据邮箱查询用户信息
    UserAuthDto selectUserByEmail(String email);

    @Insert("INSERT INTO user_roles(user_id,role_id) VALUES(#{id},2)")
    // 默认角色为普通用户
    void insertUserRole(Long id);

    CustomUserDetails selectUserDetailsByEmail(String email);
}

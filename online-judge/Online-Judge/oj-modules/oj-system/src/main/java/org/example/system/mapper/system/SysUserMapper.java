package org.example.system.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.system.domain.sysuser.SysUser;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}

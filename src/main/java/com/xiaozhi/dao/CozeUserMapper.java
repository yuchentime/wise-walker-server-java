package com.xiaozhi.dao;

import com.xiaozhi.entity.CozeUser;

public interface CozeUserMapper {
    CozeUser selectByPrimaryKey(Integer id);

    int deleteByPrimaryKey(Integer id);

    int insert(CozeUser record);

    int updateByPrimaryKey(CozeUser record);
}
package com.xiaozhi.dao;

import com.xiaozhi.entity.CozeUser;

public interface CozeUserMapper {

    CozeUser selectByCozeUser(CozeUser record);

    int insert(CozeUser record);

    int updateByCozeUser(CozeUser record);
}
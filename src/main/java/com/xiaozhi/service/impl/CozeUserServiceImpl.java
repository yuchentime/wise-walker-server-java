package com.xiaozhi.service.impl;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.xiaozhi.dao.CozeUserMapper;
import com.xiaozhi.entity.CozeUser;
import com.xiaozhi.service.CozeUserService;

import jakarta.annotation.Resource;

@Service
public class CozeUserServiceImpl implements CozeUserService {

    private Logger logger = LoggerFactory.getLogger(CozeUserServiceImpl.class);

    @Resource
    private CozeUserMapper cozeUserMapper;

    @Override
    public void saveInAsync(CozeUser cozeUser) {
        if (cozeUser == null 
            || StringUtils.isBlank(cozeUser.getCozeUserId()) 
            || StringUtils.isBlank(cozeUser.getDataSource())) {
            logger.error("saveInAsync error, cozeUser:{}", cozeUser);
            return;
        }
        CozeUser cozeUserInDb = cozeUserMapper.selectByCozeUser(cozeUser);
        if (cozeUserInDb == null) {
            cozeUserMapper.insert(cozeUser);
        } else {
            if (cozeUser.getAge() != null) {
                cozeUserInDb.setAge(cozeUser.getAge());
            }
            if (StringUtils.isNotBlank(cozeUser.getGender())) {
                cozeUserInDb.setGender(cozeUser.getGender());
            }
            if (StringUtils.isNotBlank(cozeUser.getName())) {
                cozeUserInDb.setName(cozeUser.getName());
            }
            if (StringUtils.isNotBlank(cozeUser.getPhone())) {
                cozeUserInDb.setPhone(cozeUser.getPhone());
            }
            if (StringUtils.isNotBlank(cozeUser.getLikes())) {
                cozeUserInDb.setLikes(cozeUser.getLikes());
            }
            cozeUserInDb.setUpdatedAt(new Date());
            cozeUserMapper.updateByCozeUser(cozeUserInDb);
        }
    }

}

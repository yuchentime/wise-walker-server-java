package com.xiaozhi.service.impl;

import com.xiaozhi.common.constants.SpecialCharactersConst;
import com.xiaozhi.dao.CozeChatHistoryMapper;
import com.xiaozhi.entity.CozeChatHistory;
import com.xiaozhi.service.CozeChatHistoryService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CozeChatHistoryServiceImpl implements CozeChatHistoryService {

    @Resource
    private CozeChatHistoryMapper cozeChatHistoryMapper;

    @Override
    public void add(CozeChatHistory cozeChatHistory) {
        cozeChatHistory.setCozeUserId(cozeChatHistory.getDataSource() + SpecialCharactersConst.UNDER_LINE + cozeChatHistory.getCozeUserId());
        cozeChatHistoryMapper.insert(cozeChatHistory);
    }

    @Override
    public List<CozeChatHistory> query(String dataSource, Date start, Date end) {
        Map<String, Object> params = new HashMap<>();
        params.put("dataSource", dataSource);
        params.put("createdAtStart", start);
        params.put("createdAtEnd", end);
        return cozeChatHistoryMapper.selectByConditions(params);
    }
}

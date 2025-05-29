package com.xiaozhi.service;

import com.xiaozhi.entity.CozeChatHistory;

import java.util.Date;
import java.util.List;

public interface CozeChatHistoryService {

    void add(CozeChatHistory cozeChatHistory);

    List<CozeChatHistory> query(String dataSource, Date start, Date end);
}

package com.xiaozhi.controller;

import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.entity.CozeChatHistory;
import com.xiaozhi.service.CozeChatHistoryService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/coze/chat-history")
public class CozeChatHistoryController {

    @Resource
    private CozeChatHistoryService cozeChatHistoryService;

    @Async("ioBatchExecutor")
    @PostMapping("/add")
    public AjaxResult add(@RequestBody CozeChatHistory cozeChatHistory) {
        cozeChatHistoryService.add(cozeChatHistory);
        return AjaxResult.success();
    }

}

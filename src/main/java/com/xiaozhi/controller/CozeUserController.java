package com.xiaozhi.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.entity.CozeUser;
import com.xiaozhi.service.CozeUserService;

import jakarta.annotation.Resource;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/coze/user")
public class CozeUserController {

    @Resource
    private CozeUserService cozeUserService;

    @PostMapping("save")
    public AjaxResult save(@RequestBody CozeUser entity) {
        cozeUserService.saveInAsync(entity);
        return AjaxResult.success();
    }
    
}

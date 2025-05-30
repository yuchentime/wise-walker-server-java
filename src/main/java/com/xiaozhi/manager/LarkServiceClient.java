package com.xiaozhi.manager;

import cn.hutool.core.collection.CollectionUtil;
import jakarta.annotation.Resource;

import com.google.gson.JsonParser;
import com.lark.oapi.Client;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.service.bitable.v1.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 将数据同步到指定飞书多维表格前，需要确保应用已拥有该表格的权限：多维表格右上角的[...] -> [...更多] -> [添加文档应用]
 */
@Component
public class LarkServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(LarkServiceClient.class);

    @Resource
    private Client larkClient;

    /**
     * @param records key: 列名，value: 值
     * @throws Exception
     */
    public void createRecord(String appToken, String tableId, List<Map<String, Object>> records) {
        if (CollectionUtil.isEmpty(records)) {
            return;
        }
        AppTableRecord[] appTableRecords = records.stream().map(recordMap -> AppTableRecord.newBuilder()
                .fields(recordMap)
                .build()).toArray(AppTableRecord[]::new);
        BatchCreateAppTableRecordReq req = BatchCreateAppTableRecordReq.newBuilder()
                .batchCreateAppTableRecordReqBody(BatchCreateAppTableRecordReqBody.newBuilder()
                        .records(appTableRecords)
                        .build())
//                这里appToken和tableId截取自多维表格URL中的部分：https://p0zl0uqcs23.feishu.cn/base/IcIQbsh7JaXYZzsjJ82cM0VFnYc?table=tblNHaqwAN7H0Syl&view=vewOrZDQk7
                .appToken(appToken)
                .tableId(tableId)
                .build();

        // 发起请求
        try {
            BatchCreateAppTableRecordResp resp = larkClient.bitable().v1().appTableRecord().batchCreate(req);

            // 处理服务端错误
            if (!resp.success()) {
                logger.error("批量添加记录失败. code:%s,msg:%s,reqId:%s, resp:%s", resp.getCode(), resp.getMsg(), resp.getRequestId(), Jsons.createGSON(true, false).toJson(JsonParser.parseString(new String(resp.getRawResponse().getBody(), StandardCharsets.UTF_8))));
            }

        } catch (Exception e) {
            logger.error("批量添加记录失败: ", e);
        }
    }

    public Integer getRecordCount(String appToken, String tableId, String viewId) {
        SearchAppTableRecordReq req = SearchAppTableRecordReq.newBuilder()
                .appToken(appToken)
                .tableId(tableId)
                .pageSize(10)
                .searchAppTableRecordReqBody(SearchAppTableRecordReqBody.newBuilder()
                        .viewId(viewId)
                        .automaticFields(false)
                        .build())
                .build();

        try {
            SearchAppTableRecordResp resp = larkClient.bitable().v1().appTableRecord().search(req);

            // 处理服务端错误
            if (!resp.success()) {
                logger.error("获取多维表格数据失败.code:%s,msg:%s,reqId:%s, resp:%s", resp.getCode(), resp.getMsg(), resp.getRequestId(), Jsons.createGSON(true, false).toJson(JsonParser.parseString(new String(resp.getRawResponse().getBody(), StandardCharsets.UTF_8))));
                return -1;
            }

            return resp.getData().getTotal();
        } catch (Exception e) {
            logger.error("获取多维表格数据失败: ", e);
        }
        return -1;
    }

}

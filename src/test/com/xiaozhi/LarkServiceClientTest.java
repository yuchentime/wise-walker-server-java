package com.xiaozhi;

import com.xiaozhi.manager.LarkServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.web.WebAppConfiguration;

import javax.annotation.Resource;

@SpringBootTest
@WebAppConfiguration
public class LarkServiceClientTest {

    @Resource
    private LarkServiceClient larkServiceClient;

    @Test()
    public void test2() throws Exception {
//        List<AppChatHistory> chatHistoryList = new ArrayList<>();
//        for (int i = 0; i < 10; i++) {
//            AppChatHistory chatHistory = new AppChatHistory();
//            chatHistory.setContent("测试数据" + i);
//            chatHistory.setDataSource("测试数据" + i);
//            chatHistory.setExternalUserId("测试数据" + i);
//            chatHistoryList.add(chatHistory);
//        }
//        larkServiceClient.createRecord(chatHistoryList);
        Integer count = larkServiceClient.getRecordCount("IcIQbsh7JaXYZzsjJ82cM0VFnYc", "tblNHaqwAN7H0Syl","vewOrZDQk7");
        System.out.println(count);
    }
}

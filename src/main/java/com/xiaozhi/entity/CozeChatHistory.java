package com.xiaozhi.entity;
import java.util.Date;

public class CozeChatHistory {
    private Integer id;
    private String dataSource;
    private String cozeUserId;
    private String content;
    private Date createdAt;

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public String getCozeUserId() {
        return cozeUserId;
    }

    public void setCozeUserId(String cozeUserId) {
        this.cozeUserId = cozeUserId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
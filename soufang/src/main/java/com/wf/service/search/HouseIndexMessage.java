package com.wf.service.search;

public class HouseIndexMessage {
    public static final String INDEX = "index";
    public static final String REMOVE = "remove";
    //最大重试次数
    public static final int MAX_RETRY = 3;

    private Long houseId;
    private String operation;
    private int retry;

    public HouseIndexMessage() {
    }

    public HouseIndexMessage(Long houseId, String operation, int retry) {
        this.houseId = houseId;
        this.operation = operation;
        this.retry = retry;
    }

    public Long getHouseId() {
        return houseId;
    }

    public void setHouseId(Long houseId) {
        this.houseId = houseId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public int getRetry() {
        return retry;
    }

    public void setRetry(int retry) {
        this.retry = retry;
    }
}

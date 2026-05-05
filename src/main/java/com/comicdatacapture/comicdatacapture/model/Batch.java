package com.comicdatacapture.comicdatacapture.model;

public class Batch {
    private String batchId;
    private String batchPath;
    private String sessionId;
    private String itemId;
    private Integer photoCount;

    public Batch(String batchId, String batchPath, String sessionId, String itemId, Integer photoCount){
        this.batchId = batchId;
        this.batchPath = batchPath;
        this.sessionId = sessionId;
        this.itemId = itemId;
        this.photoCount = photoCount;

    }

    public String getBatchId(){
        return batchId;
    }

    public String getBatchPath() {return batchPath; }

    public String getSessionId(){
        return sessionId;
    }

    public String getItemId(){
        return itemId;
    }

    public Integer getPhotoCount(){
        return photoCount;
    }
}
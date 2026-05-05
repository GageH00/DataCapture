package com.comicdatacapture.comicdatacapture.model;

/**
 * Represents the information required to transfer and capture all photos attached
 * to a single batch entry. These elements should be capable of handoff to an OCR application
 */
public class CapturedContent {
    private String photoId;
    private String batchId;
    private String filePath;
    private String batchPath;
    private String photoType;
    private String capturedAt;
    private Boolean frontCapturedState;
    private Boolean backCapturedState;

    public void setCapturedContent(String photoId, String batchId, String filePath, String batchPath, String photoType, String capturedAt, Boolean frontCapturedState, Boolean backCapturedState){
        this.photoId = photoId;
        this.batchId = batchId;
        this.filePath = filePath;
        this.batchPath = batchPath;
        this.photoType = photoType;
        this.capturedAt = capturedAt;
        this.frontCapturedState = frontCapturedState;
        this.backCapturedState = backCapturedState;
    }

    public String getPhotoId(){
        return photoId;
    }

    public String getBatchId(){
        return batchId;
    }

    public String getFilePath(){
        return filePath;
    }

    public String getBatchPath(){
        return batchPath;
    }

    public String getPhotoType(){
        return photoType;
    }

    public String getCapturedAt(){
        return capturedAt;
    }

    public Boolean getFrontCapturedState() { return frontCapturedState; }

    public Boolean getBackCapturedState() { return backCapturedState; }

}
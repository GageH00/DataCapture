package com.comicdatacapture.comicdatacapture.model;

/**
 * A single run time of the application
 */
public class Session {

    private String sessionId;
    private String startedId;

    public Session(String sessionId,String startedId){
        this.sessionId = sessionId;
        this.startedId = startedId;
    }

    public String getSessionId(){
        return  sessionId;
    }

    public String getStartedId(){
        return  startedId;
    }
}
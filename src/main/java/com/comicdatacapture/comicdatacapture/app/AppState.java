package com.comicdatacapture.comicdatacapture.app;

import com.comicdatacapture.comicdatacapture.model.CameraConfig;
import com.comicdatacapture.comicdatacapture.model.CaptureProfile;
import com.comicdatacapture.comicdatacapture.model.Session;

/**
 * Application-wide singleton holding runtime state shared across controllers.
 * Not persisted — cleared when the app closes.
 */
public class AppState {

    private static final AppState INSTANCE = new AppState();
    private AppState() {}
    public static AppState getInstance() { return INSTANCE; }

    // ── Config-time state ─────────────────────────────────────────────────────
    private CaptureProfile activeProfile = new CaptureProfile();

    // ── Session-time state ────────────────────────────────────────────────────
    private Session activeSession;
    private String  currentBatchId;

    /**
     * Set when a session ends normally (End Session button clicked).
     * sessionEndController reads this to query the summary and run exports.
     * Cleared when the operator starts a new session.
     */
    private String completedSessionId;
    private String completedSessionEndTime;  // local datetime, set when End Session is clicked

    // ── Profile / camera / folder ─────────────────────────────────────────────

    public CaptureProfile getActiveProfile()                   { return activeProfile; }
    public void setActiveProfile(CaptureProfile p)             { this.activeProfile = p; }

    public CameraConfig getCameraAConfig()                     { return activeProfile.getCameraA(); }
    public void setCameraAConfig(CameraConfig c)               { activeProfile.setCameraA(c); }

    public CameraConfig getCameraBConfig()                     { return activeProfile.getCameraB(); }
    public void setCameraBConfig(CameraConfig c)               { activeProfile.setCameraB(c); }

    public String getOutputFolder()                            { return activeProfile.getOutputFolder(); }
    public void setOutputFolder(String folder)                 { activeProfile.setOutputFolder(folder); }

    // ── Active session ────────────────────────────────────────────────────────

    public Session getActiveSession()                          { return activeSession; }
    public void setActiveSession(Session s)                    { this.activeSession = s; }

    public String getCurrentBatchId()                          { return currentBatchId; }
    public void setCurrentBatchId(String id)                   { this.currentBatchId = id; }

    // ── Completed session (for end-of-session summary) ────────────────────────

    public String getCompletedSessionId()                      { return completedSessionId; }
    public void setCompletedSessionId(String id)               { this.completedSessionId = id; }

    public String getCompletedSessionEndTime()                 { return completedSessionEndTime; }
    public void setCompletedSessionEndTime(String t)           { this.completedSessionEndTime = t; }

    // ── Validation ────────────────────────────────────────────────────────────

    public boolean isConfigured() {
        CameraConfig a = getCameraAConfig();
        CameraConfig b = getCameraBConfig();
        String folder  = getOutputFolder();
        return a != null && a.getCameraName() != null
            && b != null && b.getCameraName() != null
            && folder != null && !folder.isBlank();
    }
}

package com.comicdatacapture.comicdatacapture.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Operator-configured profile stored before a session begins.
 * Holds camera assignments, save location, and custom field definitions.
 */
public class CaptureProfile {

    private String profileName;
    private String outputFolder;
    private CameraConfig cameraA;
    private CameraConfig cameraB;
    private String resolution;           // e.g. "1280x720"
    private List<FieldDefinition> fields = new ArrayList<>();

    public CaptureProfile() {}

    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }

    public String getOutputFolder() { return outputFolder; }
    public void setOutputFolder(String outputFolder) { this.outputFolder = outputFolder; }

    public CameraConfig getCameraA() { return cameraA; }
    public void setCameraA(CameraConfig cameraA) { this.cameraA = cameraA; }

    public CameraConfig getCameraB() { return cameraB; }
    public void setCameraB(CameraConfig cameraB) { this.cameraB = cameraB; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public List<FieldDefinition> getFields() { return fields; }
    public void setFields(List<FieldDefinition> fields) { this.fields = fields; }

    public void addField(FieldDefinition field) { this.fields.add(field); }
    public void removeField(FieldDefinition field) { this.fields.remove(field); }
}

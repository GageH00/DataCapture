package com.comicdatacapture.comicdatacapture.model;

public class CameraConfig {

    private String slotName;    // "Camera A" or "Camera B"
    private String cameraName;  // device identifier e.g. "USB Camera 0"
    private int width;
    private int height;
    private boolean enabled;

    public String getSlotName() { return slotName; }
    public void setSlotName(String slotName) { this.slotName = slotName; }

    public String getCameraName() { return cameraName; }
    public void setCameraName(String cameraName) { this.cameraName = cameraName; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Parses a "1280x720" resolution string and applies it. */
    public void applyResolution(String resolution) {
        if (resolution == null || !resolution.contains("x")) return;
        String[] parts = resolution.split("x");
        try {
            this.width = Integer.parseInt(parts[0].trim());
            this.height = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public String toString() {
        return slotName + " [" + cameraName + " " + width + "x" + height + "]";
    }
}

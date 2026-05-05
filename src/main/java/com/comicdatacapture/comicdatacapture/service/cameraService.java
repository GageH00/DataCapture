package com.comicdatacapture.comicdatacapture.service;

import com.comicdatacapture.comicdatacapture.model.CameraConfig;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_videoio;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton service managing live camera preview workers and still captures.
 *
 * MSMF exclusive-access fix
 * ══════════════════════════
 * captureStill() no longer opens its own VideoCapture.  Instead it calls
 * grabStill() on the already-running preview worker, which returns the next
 * frame from the existing open handle.  This means the device is opened
 * exactly once per camera, never twice simultaneously.
 *
 * If no preview worker is running for the requested camera (e.g. the operator
 * captures before starting a preview), captureStill() falls back to opening a
 * short-lived VideoCapture of its own — safe in that case because no other
 * handle is competing.
 */
public class cameraService {

    private static final cameraService INSTANCE = new cameraService();
    public static cameraService getInstance() { return INSTANCE; }

    private final Map<Integer, Integer>           workingBackend = new HashMap<>();
    private final Map<String, cameraPreviewWorker> activePreviews = new HashMap<>();
    private int discoveredCameraCount = 0;

    private cameraService() {}

    // ── Startup warmup ────────────────────────────────────────────────────────

    public void warmup() {
        workingBackend.clear();
        int found = 0;
        for (int i = 0; i < 4; i++) {
            int backend = probeBackend(i);
            if (backend >= 0) {
                workingBackend.put(i, backend);
                found++;
                System.out.println("[cameraService] Camera " + i
                        + " → " + backendName(backend));
            } else {
                break;
            }
        }
        discoveredCameraCount = found;
        System.out.println("[cameraService] Warmup complete — " + found + " camera(s).");
    }

    private int probeBackend(int index) {
        // Try MSMF first (preferred on Windows 8+), then auto-select.
        // CAP_DSHOW is intentionally skipped — on this system it throws
        // "backend available but can't capture by index".
        int[] backends = { opencv_videoio.CAP_MSMF, opencv_videoio.CAP_ANY };
        for (int backend : backends) {
            VideoCapture vc = new VideoCapture();
            try {
                if (vc.open(index, backend) && vc.isOpened()) return backend;
            } finally {
                if (vc.isOpened()) vc.release();
            }
        }
        return -1;
    }

    private String backendName(int b) {
        if (b == opencv_videoio.CAP_MSMF)  return "CAP_MSMF";
        if (b == opencv_videoio.CAP_DSHOW) return "CAP_DSHOW";
        if (b == opencv_videoio.CAP_ANY)   return "CAP_ANY";
        return "backend(" + b + ")";
    }

    public int  getDiscoveredCameraCount()   { return discoveredCameraCount; }
    public int  getBackendForIndex(int idx)  { return workingBackend.getOrDefault(idx, opencv_videoio.CAP_ANY); }

    // ── Preview ───────────────────────────────────────────────────────────────

    public void startPreview(String cameraId, ImageView view, Label overlayLabel) {
        stopPreview(cameraId);
        int index   = resolveIndex(cameraId);
        int backend = getBackendForIndex(index);
        cameraPreviewWorker w =
                new cameraPreviewWorker(index, backend, cameraId, view, overlayLabel);
        activePreviews.put(cameraId, w);
        w.start();
    }

    public void startPreview(CameraConfig config, ImageView view) {
        if (config == null || config.getCameraName() == null) return;
        startPreview(config.getCameraName(), view, null);
    }

    /**
     * Starts a preview worker and blocks until the camera is actively streaming
     * (first frame received) or the 5-second timeout elapses.
     *
     * Use this instead of startPreview() anywhere a capture may follow immediately,
     * to ensure grabStill() is called after the worker has already produced frames.
     * Call from a background thread — never the JavaFX Application Thread.
     */
    public boolean startPreviewAndWait(String cameraId, ImageView view, Label overlayLabel) {
        stopPreview(cameraId);
        int index   = resolveIndex(cameraId);
        int backend = getBackendForIndex(index);
        cameraPreviewWorker w =
                new cameraPreviewWorker(index, backend, cameraId, view, overlayLabel);
        activePreviews.put(cameraId, w);
        w.start();
        boolean ready = w.waitForFirstFrame(5000);
        if (!ready) {
            System.err.println("[cameraService] startPreviewAndWait: timeout for " + cameraId);
        }
        return ready;
    }

    public void stopPreview(String cameraId) {
        cameraPreviewWorker w = activePreviews.remove(cameraId);
        if (w != null) w.stop();
    }

    public void stopAll() {
        activePreviews.values().forEach(cameraPreviewWorker::stop);
        activePreviews.clear();
    }

    public void stopAllAndWait() {
        for (cameraPreviewWorker w : activePreviews.values()) w.stopAndWait(2000);
        activePreviews.clear();
    }

    public boolean isRunning(String cameraId) {
        cameraPreviewWorker w = activePreviews.get(cameraId);
        return w != null && w.isRunning();
    }

    // ── Still capture ─────────────────────────────────────────────────────────

    /**
     * Captures a still image for the named camera and saves it to disk.
     *
     * Strategy:
     *   1. If a preview worker is active for this camera: call grabStill()
     *      which waits for the next frame from the already-open VideoCapture.
     *      The device handle is never duplicated — MSMF stays happy.
     *
     *   2. If no preview worker is running (unusual): open a short-lived
     *      VideoCapture, grab a frame, close it.  Safe because no other
     *      handle is competing.
     */
    public String captureStill(String cameraId, String slotName,
                                String outputFolder, String sessionId, String batchId) {
        try {
            Path dir = Paths.get(outputFolder,
                    "session_" + sessionId, "batch_" + batchId);
            Files.createDirectories(dir);
            String filename = slotName + "_" + System.currentTimeMillis() + ".jpg";
            String filePath = dir.resolve(filename).toAbsolutePath().toString();

            Mat frame = grabFrame(cameraId);
            if (frame == null || frame.empty()) {
                System.err.println("[cameraService] captureStill: no frame from " + cameraId);
                if (frame != null) frame.release();
                return null;
            }

            boolean saved = opencv_imgcodecs.imwrite(filePath, frame);
            frame.release();

            if (!saved) {
                System.err.println("[cameraService] captureStill: imwrite failed: " + filePath);
                return null;
            }
            System.out.println("[cameraService] Saved: " + filePath);
            return filePath;

        } catch (Exception e) {
            System.err.println("[cameraService] captureStill error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns a Mat frame for the given camera.
     * Prefers the active preview worker's shared handle; falls back to a
     * standalone VideoCapture only when no worker is running.
     */
    private Mat grabFrame(String cameraId) {
        cameraPreviewWorker worker = activePreviews.get(cameraId);

        if (worker != null && worker.isRunning()) {
            // Reuse the worker's open handle — no second VideoCapture opened
            return worker.grabStill();
        }

        // Fallback: no active preview — open a standalone capture
        int index   = resolveIndex(cameraId);
        int backend = getBackendForIndex(index);
        VideoCapture vc = new VideoCapture();
        try {
            if (!vc.open(index, backend) || !vc.isOpened()) {
                System.err.println("[cameraService] grabFrame fallback: could not open " + cameraId);
                return null;
            }
            // Discard a few frames so auto-exposure settles
            Mat frame = new Mat();
            for (int i = 0; i < 5; i++) vc.read(frame);
            Mat result = new Mat();
            frame.copyTo(result);
            frame.release();
            return result;
        } finally {
            if (vc.isOpened()) vc.release();
        }
    }

    // ── Index resolution ──────────────────────────────────────────────────────

    public int resolveIndex(String id) {
        if (id == null) return 0;
        String t = id.trim();
        try { return Integer.parseInt(t); } catch (NumberFormatException ignored) {}
        String[] parts = t.split("\\s+");
        if (parts.length > 0) {
            try { return Integer.parseInt(parts[parts.length - 1]); }
            catch (NumberFormatException ignored) {}
        }
        System.err.println("[cameraService] Could not resolve index for '" + id + "' — defaulting to 0.");
        return 0;
    }

    public List<String> getAvailableCameraNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < discoveredCameraCount; i++) names.add("USB Camera " + i);
        return names;
    }
}

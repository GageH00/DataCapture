package com.comicdatacapture.comicdatacapture.service;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Background thread that captures live frames via OpenCV and pushes them
 * to a JavaFX ImageView.
 *
 * MSMF exclusive-access fix
 * ══════════════════════════
 * Windows Media Foundation (MSMF) enforces exclusive device access — only one
 * VideoCapture can have a camera open at a time.  Opening a second
 * VideoCapture in captureStill() while the preview loop already holds the
 * device causes MF_E_HW_MFT_FAILED_START_STREAMING (-1072873821).
 *
 * The fix: captureStill() no longer opens its own VideoCapture.  Instead it
 * calls grabStill() on the worker that already has the device open.
 * grabStill() signals the capture loop to grab the next available frame,
 * stores it atomically, and returns it to the caller — all using the single
 * existing VideoCapture handle.
 */
public class cameraPreviewWorker {

    private static final int  TARGET_FPS     = 30;
    private static final long FRAME_DELAY_MS = 1000L / TARGET_FPS;

    private final int       cameraIndex;
    private final int       backend;
    private final String    cameraId;
    private final ImageView targetView;
    private final Label     overlayLabel;

    private volatile boolean         running        = false;
    private volatile Mat             stillSlot      = null;
    private volatile CountDownLatch  stillLatch     = null;
    private final    CountDownLatch  firstFrameLatch = new CountDownLatch(1);

    private Thread       thread;
    private VideoCapture capture;

    public cameraPreviewWorker(int cameraIndex, int backend, String cameraId,
                                ImageView targetView, Label overlayLabel) {
        this.cameraIndex = cameraIndex;
        this.backend     = backend;
        this.cameraId    = cameraId;
        this.targetView  = targetView;
        this.overlayLabel = overlayLabel;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        if (running) return;
        running = true;

        thread = new Thread(() -> {
            capture = new VideoCapture();
            try {
                if (!capture.open(cameraIndex, backend) || !capture.isOpened()) {
                    showError("Could not open camera: " + cameraId);
                    return;
                }
                Platform.runLater(() -> {
                    if (overlayLabel != null) overlayLabel.setVisible(false);
                });

                Mat frame = new Mat();
                boolean firstFrame = true;

                while (running) {
                    long frameStart = System.currentTimeMillis();

                    if (capture.read(frame) && !frame.empty()) {

                        // Signal that the camera is streaming on the very first frame.
                        // startPreviewAndWait() blocks on this latch.
                        if (firstFrame) {
                            firstFrameLatch.countDown();
                            firstFrame = false;
                        }

                        // ── Still grab requested? ────────────────────────────
                        CountDownLatch latch = stillLatch;
                        if (latch != null) {
                            // Copy this frame as the still result
                            Mat copy = new Mat();
                            frame.copyTo(copy);
                            stillSlot  = copy;
                            stillLatch = null;
                            latch.countDown();
                        }

                        // ── Push to live preview ─────────────────────────────
                        WritableImage img = matToWritableImage(frame);
                        if (img != null) {
                            Platform.runLater(() -> targetView.setImage(img));
                        }
                    }

                    long sleep = FRAME_DELAY_MS - (System.currentTimeMillis() - frameStart);
                    if (sleep > 0) Thread.sleep(sleep);
                }
                frame.release();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                showError("Camera error: " + e.getMessage());
                System.err.println("[cameraPreviewWorker] Error ["
                        + cameraId + "]: " + e.getMessage());
            } finally {
                // Unblock any caller waiting on firstFrameLatch or grabStill()
                firstFrameLatch.countDown();
                CountDownLatch latch = stillLatch;
                if (latch != null) {
                    stillSlot  = null;
                    stillLatch = null;
                    latch.countDown();
                }
                releaseCapture();
                Platform.runLater(() -> {
                    if (overlayLabel != null) overlayLabel.setVisible(true);
                });
            }
        }, "camera-preview-" + cameraId);

        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    public void stopAndWait(long timeoutMs) {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try { thread.join(timeoutMs); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        releaseCapture();
        thread = null;
    }

    public boolean isRunning() { return running; }

    // ── Still capture via shared handle ──────────────────────────────────────

    /**
     * Blocks until the camera has delivered its first frame (device fully open
     * and streaming), or until timeoutMs elapses.
     *
     * Call this after start() — or use cameraService.startPreviewAndWait() which
     * calls it automatically — before any grabStill() to avoid timeout races.
     */
    public boolean waitForFirstFrame(long timeoutMs) {
        try {
            return firstFrameLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Requests the next available frame and blocks until it arrives (≤ 2 s).
     * Never opens a second VideoCapture — MSMF exclusive access is respected.
     * Must be called from a background thread, never the JavaFX Application Thread.
     */
    public Mat grabStill() {
        if (!running || thread == null) return null;

        stillSlot  = null;
        CountDownLatch latch = new CountDownLatch(1);
        stillLatch = latch;

        try {
            boolean arrived = latch.await(2, TimeUnit.SECONDS);
            if (!arrived) {
                stillLatch = null;   // cancel the pending request
                System.err.println("[cameraPreviewWorker] grabStill timeout for " + cameraId);
                return null;
            }
            return stillSlot;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ── BGR Mat → JavaFX WritableImage ────────────────────────────────────────

    private WritableImage matToWritableImage(Mat mat) {
        int w = mat.cols(), h = mat.rows(), ch = mat.channels();
        WritableImage img = new WritableImage(w, h);
        PixelWriter pw = img.getPixelWriter();
        ByteBuffer buf = mat.createBuffer();
        byte[] row = new byte[w * ch];
        for (int y = 0; y < h; y++) {
            buf.position(y * w * ch);
            buf.get(row);
            for (int x = 0; x < w; x++) {
                int base = x * ch;
                int b, g, r;
                if (ch >= 3) {
                    b = row[base]     & 0xFF;
                    g = row[base + 1] & 0xFF;
                    r = row[base + 2] & 0xFF;
                } else {
                    b = g = r = row[base] & 0xFF;
                }
                pw.setArgb(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private void releaseCapture() {
        if (capture != null && capture.isOpened()) {
            try { capture.release(); }
            catch (Exception e) {
                System.err.println("[cameraPreviewWorker] Release error: " + e.getMessage());
            }
        }
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            targetView.setImage(null);
            if (overlayLabel != null) {
                overlayLabel.setText(msg);
                overlayLabel.setVisible(true);
            }
        });
    }
}

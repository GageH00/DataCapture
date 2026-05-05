module com.comicdatacapture.comicdatacapture {

    // ── JavaFX ────────────────────────────────────────────────────────────────
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;

    // ── UI utilities ──────────────────────────────────────────────────────────
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;

    // ── Database ──────────────────────────────────────────────────────────────
    requires java.sql;

    // ── Serialization ─────────────────────────────────────────────────────────
    requires com.google.gson;
    requires org.bytedeco.opencv;

    // ── Camera (JavaCV / OpenCV) ──────────────────────────────────────────────
    // JavaCV is NOT a named JPMS module — it ships as a collection of
    // automatic-module JARs.  However, because we run from a fat JAR produced
    // by maven-shade-plugin, ALL dependencies (including JavaCV) end up on the
    // CLASSPATH inside the shaded JAR, not on the module path.
    //
    // The application module reads the unnamed module automatically, so
    // cameraService and cameraPreviewWorker can import org.bytedeco.* classes
    // without any `requires` declaration here — the compiler sees them on the
    // classpath via `--class-path` rather than `--module-path`.
    //
    // When running from IntelliJ (not shaded), add the following to VM options
    // in the Run Configuration:
    //   --add-reads com.comicdatacapture.comicdatacapture=ALL-UNNAMED
    // This grants the module read access to all classpath JARs including JavaCV.

    // ── FXML wiring ───────────────────────────────────────────────────────────
    opens com.comicdatacapture.comicdatacapture to javafx.fxml;

    exports com.comicdatacapture.comicdatacapture.controller;
    opens  com.comicdatacapture.comicdatacapture.controller to javafx.fxml;

    exports com.comicdatacapture.comicdatacapture.app;
    opens  com.comicdatacapture.comicdatacapture.app to javafx.fxml;

    opens com.comicdatacapture.comicdatacapture.model to com.google.gson;
}

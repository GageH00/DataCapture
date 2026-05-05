package com.comicdatacapture.comicdatacapture.app;

import com.comicdatacapture.comicdatacapture.service.cameraService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class cameraIntakeApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        // Camera device discovery MUST run before any database connection.
        // JavaCV/OpenCV native libs extract to ~/.javacpp/cache/ at first use
        // via JavaCPP's Loader — this is robust across IDE/fat-JAR/module-path.
        // Running warmup() here ensures device indices are known before the
        // config view's ComboBoxes are populated.
        cameraService.getInstance().warmup();

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/com/comicdatacapture/comicdatacapture/intakeLanding-view.fxml"));

        Scene scene = new Scene(loader.load(), 1200, 800);
        stage.setTitle("Comic Intake Design");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

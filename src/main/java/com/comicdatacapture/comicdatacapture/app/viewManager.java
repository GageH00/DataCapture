package com.comicdatacapture.comicdatacapture.app;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class viewManager {

    private static final String BASE_PATH = "/com/comicdatacapture/comicdatacapture/";

    public static void setScene(Stage stage, String fxmlFile, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(viewManager.class.getResource(BASE_PATH + fxmlFile));
            Parent root = loader.load();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load view: " + fxmlFile, e);
        }
    }
}
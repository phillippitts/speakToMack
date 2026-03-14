package com.boombapcompile.blckvox.service.livecaption;

import com.boombapcompile.blckvox.config.properties.LiveCaptionProperties;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * JavaFX overlay window displaying a real-time oscilloscope waveform and caption text.
 *
 * <p>Must be created and used only on the JavaFX Application Thread.
 *
 * @since 1.3
 */
class LiveCaptionWindow {

    private static final double CANVAS_HEIGHT = 100;
    private static final double PADDING = 10;
    private static final int FONT_SIZE = 16;
    private static final Color PARTIAL_COLOR = Color.gray(0.7);

    private final Stage stage;
    private final Canvas canvas;
    private final Label captionLabel;
    private final double canvasWidth;

    LiveCaptionWindow(LiveCaptionProperties props) {
        int width = props.getWindowWidth();
        int height = props.getWindowHeight();
        canvasWidth = width - 2 * PADDING;

        canvas = new Canvas(canvasWidth, CANVAS_HEIGHT);

        captionLabel = new Label("");
        captionLabel.setFont(Font.font("System", FONT_SIZE));
        captionLabel.setTextFill(Color.WHITE);
        captionLabel.setWrapText(true);
        captionLabel.setMaxWidth(canvasWidth);
        captionLabel.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(PADDING, canvas, captionLabel);
        root.setPadding(new Insets(PADDING));
        root.setAlignment(Pos.TOP_CENTER);
        root.setBackground(new Background(new BackgroundFill(
                Color.rgb(30, 30, 30, 0.9),
                new CornerRadii(12),
                Insets.EMPTY)));

        Scene scene = new Scene(root, width, height);
        scene.setFill(Color.TRANSPARENT);

        stage = new Stage(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.setOpacity(props.getWindowOpacity());
        stage.setTitle("Live Caption");

        positionAtBottomCenter(width, height);
        drawFlatLine();
    }

    void show() {
        stage.show();
    }

    void hide() {
        stage.hide();
        drawFlatLine();
        captionLabel.setText("");
    }

    void updateWaveform(short[] samples) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double midY = CANVAS_HEIGHT / 2.0;

        gc.clearRect(0, 0, canvasWidth, CANVAS_HEIGHT);
        gc.setStroke(Color.LIMEGREEN);
        gc.setLineWidth(1.5);
        gc.beginPath();

        if (samples.length == 0) {
            gc.moveTo(0, midY);
            gc.lineTo(canvasWidth, midY);
        } else {
            double step = canvasWidth / samples.length;
            for (int i = 0; i < samples.length; i++) {
                double x = i * step;
                double y = midY - (samples[i] / 32768.0) * midY;
                if (i == 0) {
                    gc.moveTo(x, y);
                } else {
                    gc.lineTo(x, y);
                }
            }
        }
        gc.stroke();
    }

    void updateCaption(String text, boolean isFinal) {
        captionLabel.setText(text);
        captionLabel.setTextFill(isFinal ? Color.WHITE : PARTIAL_COLOR);
    }

    private void positionAtBottomCenter(int width, int height) {
        var bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX() + (bounds.getWidth() - width) / 2);
        stage.setY(bounds.getMinY() + bounds.getHeight() - height - 40);
    }

    private void drawFlatLine() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double midY = CANVAS_HEIGHT / 2.0;
        gc.clearRect(0, 0, canvasWidth, CANVAS_HEIGHT);
        gc.setStroke(Color.LIMEGREEN);
        gc.setLineWidth(1.5);
        gc.strokeLine(0, midY, canvasWidth, midY);
    }
}

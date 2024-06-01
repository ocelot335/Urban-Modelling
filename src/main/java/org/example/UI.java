package org.example;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class UI extends Application {
    public Simulation simulation;
    public Cell[][] cells;
    private final double windowHeight = 768;
    private final double windowWidth = 1024;
    private Group grid;
    private double width;
    private double height;
    private double k;
    private double cellSize;
    private Rectangle[][] rectanglesGrid;
    private boolean[][] turnedIntoUrban;
    private int uiIteration = 0;

    public UI(Cell[][] cells, Simulation simulation) {
        this.simulation = simulation;
        this.cells = cells;
    }

    @Override
    public void start(Stage primaryStage) {
        grid = new Group();
        cellSize = Simulation.getCellSize();
        width = simulation.getBoundsWidth() * Simulation.getFactor();
        height = simulation.getBoundsHeight() * Simulation.getFactor();
        if (width / windowWidth < height / windowHeight) {
            k = height / windowHeight;
        } else {
            k = width / windowWidth;
        }
        rectanglesGrid = new Rectangle[cells.length][cells[0].length];
        turnedIntoUrban = new boolean[cells.length][cells[0].length];

        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                Cell cell = cells[i][j];
                Rectangle rect = new Rectangle(cell.x / k, cell.y / k, cellSize / k, cellSize / k);
                rectanglesGrid[i][j] = rect;
                if (cell.newUrban) {
                    rect.setFill(Color.BLACK);
                    cell.newUrban = false;
                } else if (!cell.land) {
                    rect.setFill(Color.BLUE);
                } else if (cell.isUrban) {
                    rect.setFill(Color.ORANGE);
                } else {
                    rect.setFill(Color.DARKGRAY);
                }
                grid.getChildren().add(rect);
            }
        }

        grid.requestLayout();

        Button stepButton = new Button("Шаг Симуляции ВПЕРЁД");
        Button backStepButton = new Button("Шаг Симуляции НАЗАД");
        Button exportButton = new Button("Экспорт снапшота");
        HBox root = new HBox();
        root.getChildren().addAll(backStepButton, stepButton, exportButton);
        root.setAlignment(Pos.CENTER);
        root.setSpacing(10);
        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(grid);
        borderPane.setBottom(root);
        BorderPane.setAlignment(root, Pos.CENTER);
        BorderPane.setMargin(root, new javafx.geometry.Insets(10, 0, 10, 0));
        stepButton.setOnAction(e -> {
            simulation.doIteration(cells);
            uiIteration++;
            updateCity();
            grid.requestLayout();
        });
        backStepButton.setOnAction(e -> {
            if (uiIteration != 0) {
                uiIteration--;
                updateCity();
                grid.requestLayout();
            }
        });
        exportButton.setOnAction(e -> exportSnapshot(primaryStage, borderPane));

        primaryStage.setScene(new Scene(borderPane, width / k, height / k + 50));
        primaryStage.setTitle("Симуляция роста города");
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    private void exportSnapshot(Stage stage, BorderPane pane) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить снапшот");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG files (*.png)", "*.png"));
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try {
                WritableImage writableImage = new WritableImage((int) (width / k), (int) (height / k));
                grid.snapshot(null, writableImage);
                ImageIO.write(SwingFXUtils.fromFXImage(writableImage, null), "png", file);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void updateCity() {
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                Cell cell = cells[i][j];
                if (cell.newUrbanAt == uiIteration) {
                    rectanglesGrid[i][j].setFill(Color.BLACK);
                } else if (cell.newUrbanAt < uiIteration) {
                    rectanglesGrid[i][j].setFill(Color.ORANGE);
                } else if (!cell.land) {
                    rectanglesGrid[i][j].setFill(Color.BLUE);
                } else {
                    rectanglesGrid[i][j].setFill(Color.DARKGRAY);
                }
                /*
                if (cell.newUrban) {
                    rectanglesGrid[i][j].setFill(Color.BLACK);
                    cell.newUrban = false;
                    turnedIntoUrban[i][j] = true;
                } else if (turnedIntoUrban[i][j]) {
                    turnedIntoUrban[i][j] = false;
                    rectanglesGrid[i][j].setFill(Color.ORANGE);
                }
                */
            }
        }
    }
}
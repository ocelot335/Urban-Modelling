package org.example;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

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

        Button stepButton = new Button("Шаг Симуляции");
        VBox root = new VBox();
        root.getChildren().addAll(grid, stepButton);
        root.setAlignment(Pos.CENTER);
        root.setSpacing(10);
        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(root);
        stepButton.setOnAction(e -> {
            simulation.doIteration(cells);
            updateCity();
            grid.requestLayout();
        });
        primaryStage.setScene(new Scene(borderPane, width / k, height / k + 50));
        primaryStage.setTitle("Симуляция роста города");
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    private void updateCity() {
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                Cell cell = cells[i][j];
                if (cell.newUrban) {
                    rectanglesGrid[i][j].setFill(Color.BLACK);
                    cell.newUrban = false;
                    turnedIntoUrban[i][j] = true;
                } else if (turnedIntoUrban[i][j]) {
                    turnedIntoUrban[i][j] = false;
                    rectanglesGrid[i][j].setFill(Color.ORANGE);
                }
            }
        }
    }
}
package org.example;

import javafx.application.Application;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class CityGrowthApplication extends Application {

    private Simulation simulation;
    private Service<Void> simulationService;
    private VBox root;
    private String path;
    private Cell[][] cellsOfSimulation;

    public CityGrowthApplication() {
        root = new VBox();
        root.setPadding(new Insets(10));
        root.setSpacing(10);
    }

    @Override
    public void start(Stage primaryStage) {

        // Создаем метку для выбора папки с входными данными
        Label inputFolderLabel = new Label("Выберите папку с входными данными:");


        Button inputFolderButton = new Button("Выбрать папку shape");
        Button settingsButton = new Button("Настройки симуляции");
        Button startButton = new Button("Запустить симуляцию");

        // Создаем кнопку для выбора папки
        inputFolderButton.setOnAction(e -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            File selectedDirectory = directoryChooser.showDialog(primaryStage);
            if (selectedDirectory != null) {
                path = selectedDirectory.getAbsolutePath();
                startButton.setDisable(false);
            }
        });

        // Добавляем метку и кнопку в корневой элемент
        root.getChildren().addAll(inputFolderLabel, inputFolderButton);

        settingsButton.setOnAction(e -> {
            Stage settingsStage = new Stage();
            settingsStage.initOwner(primaryStage);
            settingsStage.initModality(Modality.APPLICATION_MODAL);
            settingsStage.setTitle("Настройки симуляции");

            GridPane settingsPane = new GridPane();
            settingsPane.setPadding(new Insets(10));
            settingsPane.setHgap(10);
            settingsPane.setVgap(10);

            Label cellSizeLabel = new Label("Уровень детализации:");
            ComboBox<String> cellSizeBox = new ComboBox<>();
            cellSizeBox.getItems().addAll("Высокий", "Средний", "Низкий");
            if (Simulation.getCellSize() == 40) {
                cellSizeBox.setValue("Высокий");
            } else if (Simulation.getCellSize() == 120) {
                cellSizeBox.setValue("Средний");
            } else {
                cellSizeBox.setValue("Низкий");
            }

            settingsPane.add(cellSizeLabel, 0, 0);
            settingsPane.add(cellSizeBox, 1, 0);

            Label mConstLabel = new Label("Сторона квадрата соседства:\n(m константа, нечётное число>1)");
            TextField mConstField = new TextField(String.valueOf(Simulation.getM_CONST()));
            mConstField.setPrefWidth(50);
            settingsPane.add(mConstLabel, 0, 1);
            settingsPane.add(mConstField, 1, 1);

            Label scanningRadiusLabel = new Label("Радиус сканирования:\n(В метрах)");
            TextField scanningRadiusField = new TextField(String.valueOf(Simulation.getSCANNING_RADIUS()));
            scanningRadiusField.setPrefWidth(50);
            settingsPane.add(scanningRadiusLabel, 0, 2);
            settingsPane.add(scanningRadiusField, 1, 2);

            Label degreeOfPerturbationLabel = new Label("Степень случайности:\n(Возмущения)");
            TextField degreeOfPerturbationField = new TextField(String.valueOf(Simulation.getDegreeOfPerturbation()));
            degreeOfPerturbationField.setPrefWidth(50);
            settingsPane.add(degreeOfPerturbationLabel, 0, 3);
            settingsPane.add(degreeOfPerturbationField, 1, 3);

            Label segmentationDegreeLabel = new Label("Стпень сегментации\n(Число от 0.2 до 0.9)");
            TextField segmentationDegreeField = new TextField(String.valueOf(Simulation.getDegreeOfSegmentation()));
            segmentationDegreeField.setPrefWidth(50);
            settingsPane.add(segmentationDegreeLabel, 0, 4);
            settingsPane.add(segmentationDegreeField, 1, 4);

            Label minSpeedForExpresswayLabel = new Label("Минимальная скорость \"скоростных\" дорог:\n(В км/ч, натуральное число)");
            TextField minSpeedForExpresswayField = new TextField(String.valueOf(Simulation.getMinSpeedForExpressway()));
            minSpeedForExpresswayField.setPrefWidth(50);
            settingsPane.add(minSpeedForExpresswayLabel, 0, 5);
            settingsPane.add(minSpeedForExpresswayField, 1, 5);

            Label defaultRiverWidthLabel = new Label("Ширина рек по умолчанию\n(В метрах)");
            TextField defaultRiverWidthField = new TextField(String.valueOf(Simulation.getDefaultRiverWidth()));
            defaultRiverWidthField.setPrefWidth(50);
            settingsPane.add(defaultRiverWidthLabel, 0, 6);
            settingsPane.add(defaultRiverWidthField, 1, 6);


            Label b0Label = new Label("b0\n(Константа):");
            TextField b0Field = new TextField(String.valueOf(Simulation.getB0()));
            b0Field.setPrefWidth(50);
            settingsPane.add(b0Label, 2, 0);
            settingsPane.add(b0Field, 3, 0);

            Label bRDLabel = new Label("bRD\n(Не скоростные дороги):");
            TextField bRDField = new TextField(String.valueOf(Simulation.getBRD()));
            bRDField.setPrefWidth(50);
            settingsPane.add(bRDLabel, 2, 1);
            settingsPane.add(bRDField, 3, 1);

            Label bHWLabel = new Label("bHW\n(Скоростные дороги):");
            TextField bHWField = new TextField(String.valueOf(Simulation.getBHW()));
            bHWField.setPrefWidth(50);
            settingsPane.add(bHWLabel, 2, 2);
            settingsPane.add(bHWField, 3, 2);

            Label bRWLabel = new Label("bRW\n(Железные дороги):");
            TextField bRWField = new TextField(String.valueOf(Simulation.getBRW()));
            bRWField.setPrefWidth(50);
            settingsPane.add(bRWLabel, 2, 3);
            settingsPane.add(bRWField, 3, 3);

            Label bSWLabel = new Label("bSW\n(Метро):");
            TextField bSWField = new TextField(String.valueOf(Simulation.getBSW()));
            bSWField.setPrefWidth(50);
            settingsPane.add(bSWLabel, 2, 4);
            settingsPane.add(bSWField, 3, 4);

            Label bMCLabel = new Label("bMC\n(Городские центры):");
            TextField bMCField = new TextField(String.valueOf(Simulation.getBMC()));
            bMCField.setPrefWidth(50);
            settingsPane.add(bMCLabel, 2, 5);
            settingsPane.add(bMCField, 3, 5);

            Label bDCLabel = new Label("bDC\n(Районные центры):");
            TextField bDCField = new TextField(String.valueOf(Simulation.getBDC()));
            bDCField.setPrefWidth(50);
            settingsPane.add(bDCLabel, 2, 6);
            settingsPane.add(bDCField, 3, 6);

            Label bLTCLabel = new Label("bLTC\n(Большие сельские центры):");
            TextField bLTCField = new TextField(String.valueOf(Simulation.getBLTC()));
            bLTCField.setPrefWidth(50);
            settingsPane.add(bLTCLabel, 2, 7);
            settingsPane.add(bLTCField, 3, 7);

            Label bSTCLabel = new Label("bSTC\n(Малые сельские центры):");
            TextField bSTCField = new TextField(String.valueOf(Simulation.getBSTC()));
            bSTCField.setPrefWidth(50);
            settingsPane.add(bSTCLabel, 2, 8);
            settingsPane.add(bSTCField, 3, 8);

            Label kConstLabel = new Label("Коэффицент k:\n(Маcштаб в регресии)");
            TextField kConstField = new TextField(String.valueOf(Simulation.getKConst()));
            kConstField.setPrefWidth(50);
            settingsPane.add(kConstLabel, 2, 9);
            settingsPane.add(kConstField, 3, 9);


            Label thresholdScoreLabel = new Label("Пороговое количество очков\n(Натуральное число):");
            TextField thresholdScoreField = new TextField(String.valueOf(Simulation.getThresholdScore()));
            thresholdScoreField.setPrefWidth(50);
            settingsPane.add(thresholdScoreLabel, 4, 0);
            settingsPane.add(thresholdScoreField, 5, 0);

            Label scoreForFootwayLabel = new Label("Количество очков для тротуаров\n(Натуральное число):");
            TextField scoreForFootwayField = new TextField(String.valueOf(Simulation.getScoreForFootway()));
            scoreForFootwayField.setPrefWidth(50);
            settingsPane.add(scoreForFootwayLabel, 4, 1);
            settingsPane.add(scoreForFootwayField, 5, 1);

            Label bigBuildingsAreaLabel = new Label("Порог большой площади домов");
            TextField bigBuildingsAreaField = new TextField(String.valueOf(Simulation.getBigBuildingsArea()));
            bigBuildingsAreaField.setPrefWidth(50);
            settingsPane.add(bigBuildingsAreaLabel, 4, 2);
            settingsPane.add(bigBuildingsAreaField, 5, 2);

            Label scoreFotBigBuildingsAreaLabel = new Label("Количество очков для большой площади домов\n(Натуральное число):");
            TextField scoreForBigBuildingsAreaField = new TextField(String.valueOf(Simulation.getScoreForBigBuildingsArea()));
            scoreForBigBuildingsAreaField.setPrefWidth(50);
            settingsPane.add(scoreFotBigBuildingsAreaLabel, 4, 3);
            settingsPane.add(scoreForBigBuildingsAreaField, 5, 3);

            Label mediumBuildingsAreaLabel = new Label("Порог нормальной площади домов");
            TextField mediumBuildingsAreaField = new TextField(String.valueOf(Simulation.getMediumBuildingsArea()));
            mediumBuildingsAreaField.setPrefWidth(50);
            settingsPane.add(mediumBuildingsAreaLabel, 4, 4);
            settingsPane.add(mediumBuildingsAreaField, 5, 4);

            Label scoreForMediumBuildingsAreaLabel = new Label("Количество очков для средней площади домов\n(Натуральное число):");
            TextField scoreForMediumBuildingsAreaField = new TextField(String.valueOf(Simulation.getScoreForMediumBuildingsArea()));
            scoreForMediumBuildingsAreaField.setPrefWidth(50);
            settingsPane.add(scoreForMediumBuildingsAreaLabel, 4, 5);
            settingsPane.add(scoreForMediumBuildingsAreaField, 5, 5);

            Label thresholdForAreaForOneBuildingLabel = new Label("Порог площади для одного дома:");
            TextField thresholdForAreaForOneBuildingField = new TextField(String.valueOf(Simulation.getThresholdForAreaForOneBuilding()));
            thresholdForAreaForOneBuildingField.setPrefWidth(50);
            settingsPane.add(thresholdForAreaForOneBuildingLabel, 4, 6);
            settingsPane.add(thresholdForAreaForOneBuildingField, 5, 6);

            Label thresholdNumOfPointsLabel = new Label("Порог количества точек особенностей");
            TextField thresholdNumOfPointsField = new TextField(String.valueOf(Simulation.getThresholdNumberOfPoints()));
            thresholdNumOfPointsField.setPrefWidth(50);
            settingsPane.add(thresholdNumOfPointsLabel, 4, 7);
            settingsPane.add(thresholdNumOfPointsField, 5, 7);

            Label scoreForPointsLabel = new Label("Количество очков для точек особенностей\n(Натуральное число):");
            TextField scoreForPointsField = new TextField(String.valueOf(Simulation.getScoreForPoints()));
            scoreForPointsField.setPrefWidth(50);
            settingsPane.add(scoreForPointsLabel, 4, 8);
            settingsPane.add(scoreForPointsField, 5, 8);

            Label maxScoreForLanduseLabel = new Label("Максимальное количество очков для землепользованая\n(Натуральное число):");
            TextField maxScoreForLanduseField = new TextField(String.valueOf(Simulation.getMaxScoreForLanduse()));
            maxScoreForLanduseField.setPrefWidth(50);
            settingsPane.add(maxScoreForLanduseLabel, 4, 9);
            settingsPane.add(maxScoreForLanduseField, 5, 9);


            Button saveButton = new Button("Сохранить");
            saveButton.setOnAction(event -> {
                int cellSize;
                if (cellSizeBox.getValue().equals("Высокий")) {
                    cellSize = 40;
                } else if (cellSizeBox.getValue().equals("Средний")) {
                    cellSize = 120;
                } else {
                    cellSize = 360;
                }
                try {
                    Simulation.setCellSize(cellSize);
                    Simulation.setM_CONST(Integer.parseInt(mConstField.getText().trim()));
                    Simulation.setSCANNING_RADIUS(Double.parseDouble(scanningRadiusField.getText().trim()));
                    Simulation.setDegreeOfPerturbation(Double.parseDouble(degreeOfPerturbationField.getText().trim()));
                    Simulation.setKConst(Double.parseDouble(kConstField.getText().trim()));
                    Simulation.setDegreeOfSegmentation(Double.parseDouble(segmentationDegreeField.getText().trim()));
                    Simulation.setMinSpeedForExpressway(Integer.parseInt(minSpeedForExpresswayField.getText().trim()));
                    Simulation.setDefaultRiverWidth(Double.parseDouble(defaultRiverWidthField.getText().trim()));
                    Simulation.setB0(Double.parseDouble(b0Field.getText().trim()));
                    Simulation.setBRD(Double.parseDouble(bRDField.getText().trim()));
                    Simulation.setBHW(Double.parseDouble(bHWField.getText().trim()));
                    Simulation.setBRW(Double.parseDouble(bRWField.getText().trim()));
                    Simulation.setBSW(Double.parseDouble(bSWField.getText().trim()));
                    Simulation.setBMC(Double.parseDouble(bMCField.getText().trim()));
                    Simulation.setBDC(Double.parseDouble(bDCField.getText().trim()));
                    Simulation.setBLTC(Double.parseDouble(bLTCField.getText().trim()));
                    Simulation.setBSTC(Double.parseDouble(bSTCField.getText().trim()));
                    Simulation.setScoreForFootway(Integer.parseInt(scoreForFootwayField.getText().trim()));
                    Simulation.setThresholdScore(Integer.parseInt(thresholdScoreField.getText().trim()));
                    Simulation.setBigBuildingsArea(Double.parseDouble(bigBuildingsAreaField.getText().trim()));
                    Simulation.setScoreForBigBuildingsArea(Integer.parseInt(scoreForBigBuildingsAreaField.getText().trim()));
                    Simulation.setMediumBuildingsArea(Double.parseDouble(mediumBuildingsAreaField.getText().trim()));
                    Simulation.setScoreForMediumBuildingsArea(Integer.parseInt(scoreForMediumBuildingsAreaField.getText().trim()));
                    Simulation.setThresholdForAreaForOneBuilding(Double.parseDouble(thresholdForAreaForOneBuildingField.getText().trim()));
                    Simulation.setThresholdNumberOfPoints(Integer.parseInt(thresholdNumOfPointsField.getText().trim()));
                    Simulation.setScoreForMediumBuildingsArea(Integer.parseInt(scoreForPointsField.getText().trim()));
                    Simulation.setMaxScoreForLanduse(Integer.parseInt(maxScoreForLanduseField.getText().trim()));
                    settingsStage.close();
                } catch (NumberFormatException exception) {
                    // Показываем всплывающее окно с сообщением об ошибке
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Ошибка ввода");
                    alert.setHeaderText("Неверные параметры");
                    alert.setContentText("Пожалуйста, проверьте введенные значения и попробуйте снова.");
                    alert.showAndWait();
                }
            });
            settingsPane.add(saveButton, 2, 10, 3, 1);

            Scene settingsScene = new Scene(settingsPane);
            settingsStage.setScene(settingsScene);
            settingsStage.setResizable(false);
            settingsStage.showAndWait();
        });

// Добавляем кнопку настроек в корневой элемент
        root.getChildren().add(settingsButton);

        // Создаем кнопку для запуска симуляции
        startButton.setDisable(true);
        startButton.setOnAction(e -> {
            // Создаем диалоговое окно ожидания
            final Stage waitStage = new Stage();
            waitStage.initOwner(primaryStage);
            waitStage.initModality(Modality.APPLICATION_MODAL);
            waitStage.setResizable(false);
            waitStage.setScene(new Scene(new Label("Идет инициализация симуляции..."), 200, 50));
            // Отображаем ожидание
            waitStage.show();

            try {
                // Создаем экземпляр симуляции, передав ему необходимые параметры
                simulation = new Simulation(path);
                // Создаем сервис для выполнения симуляции в фоновом потоке
                simulationService = new Service<Void>() {
                    @Override
                    protected Task<Void> createTask() {
                        return new Task<Void>() {
                            @Override
                            protected Void call() throws Exception {
                                // Инициализация симуляции
                                cellsOfSimulation = simulation.getInitialCells();
                                return null;
                            }
                        };
                    }
                };

                // Обрабатываем событие окончания выполнения сервиса
                simulationService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
                    @Override
                    public void handle(WorkerStateEvent event) {
                        waitStage.close();
                        UI visualization = new UI(cellsOfSimulation, simulation);
                        visualization.start(primaryStage);
                    }
                });


                // Запускаем сервис
                simulationService.start();
            } catch (RuntimeException | IOException exception) {
                waitStage.close();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Ошибка данных");
                if ("Too big!".equals(exception.getMessage())) {
                    alert.setHeaderText("Слишком большой размер входных данных");
                    alert.setContentText("Пожалуйста, выберите меньший участок для симуляции и попробуйте снова.");
                } else {
                    alert.setHeaderText("Неправильный формат входных данных");
                    alert.setContentText("Пожалуйста, проверьте, что выбрали нужную папку, и попробуйте снова.\n" +
                            exception.getCause() + exception.getMessage());
                }
                alert.showAndWait();
            }
        });

        // Добавляем кнопку запуска в корневой элемент
        root.getChildren().add(startButton);

        // Создаем сцену и устанавливаем ее в окно
        Scene scene = new Scene(root, 260, 150);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.setTitle("Стартовое окно");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
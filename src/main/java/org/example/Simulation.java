package org.example;

import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import static java.lang.Double.min;

public class Simulation {

    public enum Parameters {
        ROADS,
        EXPRESS_WAYS,
        RAILWAYS,
        SUBWAYS,
        MAIN_CENTERS,
        DISTRICT_CENTERS,
        LARGE_TOWN_CENTERS,
        SMALL_TOWN_CENTERS
    }

    public enum Shapefiles {
        PLACES,
        RAILWAYS,
        ROADS,
        NATURAL,
        POINTS,
        BUILDINGS,
        WATERWAYS,
        LANDUSE
    }

    private final static double FACTOR = 111319.444;
    @Getter
    @Setter
    private static double cellSize = 40; // The size of each cell in meters
    @Setter
    @Getter
    private static int M_CONST = 3;
    private final static int NEIGHBOURHOOD_WIDTH = 3;
    private final static double W_1 = 0.5; //weight for suitability
    private final static double W_2 = 0.5; //weight for heterogeneity
    private final static double N_OMEGA = 0.5; //threshold of neighborhood density
    @Setter
    @Getter
    private static double SCANNING_RADIUS = 240; // Radius of scanning for urban features
    @Getter
    @Setter
    private static double degreeOfPerturbation = 1;
    @Getter
    @Setter
    private static double degreeOfSegmentation = 0.7;
    @Getter
    @Setter
    private static double kConst = 1;
    @Getter
    @Setter
    private static int minSpeedForExpressway = 61;
    @Getter
    @Setter
    private static double defaultRiverWidth = 60;
    @Getter
    @Setter
    private static double bigBuildingsArea = 4200, mediumBuildingsArea = 2800, thresholdForAreaForOneBuilding = 700;
    @Getter
    @Setter
    private static int scoreForBigBuildingsArea = 4, scoreForMediumBuildingsArea = 3, maxScoreForLanduse = 4;

    /*b0 = constant term; bRD = distance to road; bHW = distance to express way;
    bRW = distance to railway; bSW = distance to subway; bMC = distance to main centers;
    bDC= distance to district centers; bLTC = distance to large town centers;
    bSTC = distance to small town centers*/
    @Getter
    @Setter
    private static double b0 = 0.98, bRD = -0.35, bHW = 0.62, bRW = -0.81, bSW = -0.74,
            bMC = -0.37, bDC = 0.05, bLTC = 0.04, bSTC = -0.21;
    private final double[] regressionConstants = {b0, bRD, bHW, bRW, bSW, bMC,
            bDC, bLTC, bSTC};
    @Getter
    @Setter
    private static int thresholdScore = 7, scoreForFootway = 1, thresholdNumberOfPoints = 5, scoreForPoints = 2;




    //1. Наличие дорог и тротуаров - 2-3 очка. Дороги и тротуары являются важными признаками городской среды, так как они связывают здания и объекты в городе. Квадрат, на котором есть множество дорог и тротуаров, получит больше очков, чем квадрат, где есть только одна дорога или вообще нет дорог.
    //
    //2. Наличие зданий - 2-3 очка. Здания также являются важным признаком городской среды. Количество очков, которые будет получать квадрат, зависит от количества зданий на нем. Квадрат, где большинство площадей занято зданиями, получит больше очков, чем тот, где есть только несколько зданий.
    //
    //3. Наличие парков и зеленых зон - 1-2 очка. Парки и зеленые зоны также могут быть важными признаками городской среды, так как они способствуют экологической чистоте и улучшают качество жизни. Квадрат, где есть много парков и зеленых зон, получит больше очков, чем квадрат, где они отсутствуют.
    //
    //4. Наличие магазинов, ресторанов и кафе - 1-2 очка. Магазины, рестораны и кафе могут говорить о коммерческой активности в регионе. Квадрат, где есть много магазинов, ресторанов и кафе, также получит больше очков, чем квадрат, где их мало или вообще нет.
    //
    //5. Наличие транспорта - 1-2 очка. Наличие общественного транспорта, такого как автобусы, трамваи и метро, также может быть важным признаком городской среды. Квадрат, где есть много остановок общественного транспорта, получит больше очков, чем квадрат, где их мало или вообще нет.

    private static final String[] SHAPEFILE_PATHS = {
            File.separator+ "places.shp",
            File.separator+"railways.shp",
            File.separator+"roads.shp",
            File.separator+"natural.shp",
            File.separator+"points.shp",
            File.separator+"buildings.shp",
            File.separator+"waterways.shp",
            File.separator+"landuse.shp"
    };

    private double boundsWidth;
    private double boundsHeight;
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;
    private final GeometryFactory geometryFactory; // Used to create new geometries
    @Getter
    private final SimpleFeatureCollection[] shapefilesFeatureCollections;

    private final UrbanizationEvaluator urbanizationEvaluator;
    private final STRTree strTree;

    public double getBoundsHeight() {
        return boundsHeight;
    }

    public double getBoundsWidth() {
        return boundsWidth;
    }

    public static double getFactor() {
        return FACTOR;
    }

    public Simulation(String path) throws IOException {
        int n = SHAPEFILE_PATHS.length;
        long size = FileUtils.sizeOfDirectory(new File(path)) / (1024 * 1024);
        if (size >= 64) {
            throw new IOException("Too big!");
        }
        nu.pattern.OpenCV.loadLocally();
        shapefilesFeatureCollections = new SimpleFeatureCollection[n];
        for (int i = 0; i < n; i++) {
            File shapefile = new File(path + SHAPEFILE_PATHS[i]);
            ShapefileDataStore dataStore = null;
            try {
                dataStore = new ShapefileDataStore(shapefile.toURI().toURL());
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Get the names of all the layers in the shapefile
            String[] typeNames;
            try {
                typeNames = dataStore.getTypeNames();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Load the features
            shapefilesFeatureCollections[i] = dataStore.getFeatureSource(typeNames[0]).getFeatures();
            boundsWidth = shapefilesFeatureCollections[i].getBounds().getWidth();
            boundsHeight = shapefilesFeatureCollections[i].getBounds().getHeight();
            minX = shapefilesFeatureCollections[i].getBounds().getMinX();
            maxX = shapefilesFeatureCollections[i].getBounds().getMaxX();
            minY = shapefilesFeatureCollections[i].getBounds().getMinY();
            maxY = shapefilesFeatureCollections[i].getBounds().getMaxY();
        }

        // Initialize the geometry factory
        geometryFactory = JTSFactoryFinder.getGeometryFactory();
        strTree = new STRTree(shapefilesFeatureCollections, minSpeedForExpressway, defaultRiverWidth, FACTOR);
        urbanizationEvaluator = new UrbanizationEvaluator(cellSize, thresholdNumberOfPoints, scoreForPoints,
                maxScoreForLanduse, scoreForBigBuildingsArea, scoreForMediumBuildingsArea, scoreForFootway, thresholdScore,
                SCANNING_RADIUS,FACTOR, thresholdForAreaForOneBuilding, bigBuildingsArea, mediumBuildingsArea, strTree);
    }

    public Cell[][] getInitialCells() {
        // Calculate the number of cells in the grid
        int numCellsX = (int) Math.ceil(boundsWidth * FACTOR / cellSize);
        int numCellsY = (int) Math.ceil(boundsHeight * FACTOR / cellSize);
        // Create a 2D array to hold the cells
        Cell[][] cells = new Cell[numCellsX][numCellsY];
        // Initialize the cells with their centroid coordinates and the nearest road and city center distances
        double x = minX;
        double d_x = (boundsWidth) / numCellsX;
        double y = maxY;
        double d_y = (-boundsHeight) / numCellsY;
        for (int i = 0; i < numCellsX; i++) {
            for (int j = 0; j < numCellsY; j++) {
                Point centroid = geometryFactory.createPoint(new Coordinate(x, y));
                // Create a new cell object and store it in the array
                cells[i][j] = new Cell(minX + i * cellSize + cellSize / 2,
                        minY + j * cellSize + cellSize / 2, i, j,
                        strTree.getNearestDistances(centroid), strTree.isInWater(centroid), centroid,
                        regressionConstants, degreeOfPerturbation, kConst,
                        min(boundsHeight, boundsWidth) * FACTOR / (2));
                urbanizationEvaluator.initUrban(cells[i][j]);
                y += d_y;
            }
            y = maxY;
            x += d_x;
        }
        return cells;
    }

    public void doIteration(Cell[][] cells) {
        //calc neighbourhoodMeanUrban, RA and develeopment probability
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[0].length; j++) {
                Cell currentCell = cells[i][j];
                double sum = 0;
                double cnt = 0;
                int m = M_CONST;
                for (int k = -(m / 2); k <= (m / 2); k++) {
                    for (int l = -(m / 2); l <= (m / 2); l++) {
                        if (0 <= i + k && i + k < cells.length && j + l < cells[0].length && 0 <= j + l && (k != 0 || l != 0)) {
                            cnt++;
                            if (cells[i + k][j + l].isUrban) {
                                sum++;
                            }
                        }
                    }
                }
                currentCell.neighbourhoodMeanUrban = sum / cnt;

                //RA
                currentCell.setRA();

                //development probability
                if (currentCell.land) {
                    currentCell.developmentProbability = min(1, currentCell.RA * (currentCell.suitabilityCoefficient + currentCell.neighbourhoodMeanUrban));
                } else {
                    currentCell.developmentProbability = 0;
                }
            }
        }

        //Segmentation
        ArrayList<ArrayList<Cell>> segments = segmentation(cells);
        ArrayList<Pair<Integer, Double>> utilityAssessments = new ArrayList<>();
        //Object selection
        double urbanCount = 0;
        for (int i = 0; i < segments.size(); i++) {
            double meanDevelopmentProbabilities = 0;
            double dispersionDevelopmentProbabilities = 0;
            for (Cell cell :
                    segments.get(i)) {
                meanDevelopmentProbabilities += cell.developmentProbability;
                dispersionDevelopmentProbabilities += cell.developmentProbability * cell.developmentProbability;
                if (cell.isUrban) {
                    urbanCount++;
                }
            }
            meanDevelopmentProbabilities /= segments.get(i).size();
            dispersionDevelopmentProbabilities /= segments.get(i).size();
            dispersionDevelopmentProbabilities -= meanDevelopmentProbabilities * meanDevelopmentProbabilities;
            double standardDeviationDevelopmentProbabilities = Math.sqrt(dispersionDevelopmentProbabilities);
            utilityAssessments.add(new Pair<>(i, W_1 * meanDevelopmentProbabilities - W_2 * standardDeviationDevelopmentProbabilities));
        }

        //Sorting
        utilityAssessments.sort(new Comparator<Pair<Integer, Double>>() {
            @Override
            public int compare(Pair<Integer, Double> o1, Pair<Integer, Double> o2) {
                return -Double.compare(o1.getValue(), o2.getValue());
            }
        });
        int meanUrbanForPatch = cells.length * cells[0].length / segments.size();

        //Type detection
        for (int i = 0; i < segments.size(); i++) {
            ArrayList<Cell> currentSegment = segments.get(utilityAssessments.get(i).getKey());
            //LEInei computing
            double LEInei = 0;
            if (segments.size() == 1) {
                System.out.println("!");
            }
            for (Cell cell :
                    currentSegment) {
                if (cell.isUrban) {
                    urbanCount++;
                }
                double Ni = 0;
                int cellI = cell.i;
                int cellJ = cell.j;
                for (int k = -NEIGHBOURHOOD_WIDTH; k <= NEIGHBOURHOOD_WIDTH; k++) {
                    for (int l = -NEIGHBOURHOOD_WIDTH; l <= NEIGHBOURHOOD_WIDTH; l++) {
                        if ((k != 0 || l != 0) && 0 <= k + cellI && k + cellI < cells.length &&
                                0 <= l + cellJ && l + cellJ < cells[0].length) {
                            if (cells[k + cellI][l + cellJ].isUrban) {
                                Ni++;
                            }
                        }
                    }
                }
                Ni /= 4 * NEIGHBOURHOOD_WIDTH * NEIGHBOURHOOD_WIDTH - 1;
                LEInei += Ni;
            }
            LEInei /= currentSegment.size();


            if (LEInei > N_OMEGA) {
                //Organic
                double meanDevelopmentProbability = 0;
                for (Cell cell :
                        currentSegment) {
                    meanDevelopmentProbability += cell.developmentProbability;
                }
                meanDevelopmentProbability /= currentSegment.size();
                Random random = new Random();
                double randomValue = random.nextDouble();
                if (randomValue <= meanDevelopmentProbability) {
                    for (Cell cell :
                            currentSegment) {
                        if (!cell.isUrban && cell.land) {
                            if (cells[cell.i + 1][cell.j].curSegment != cell.curSegment &&
                                    cells[cell.i - 1][cell.j].curSegment != cell.curSegment &&
                                    cells[cell.i][cell.j + 1].curSegment != cell.curSegment &&
                                    cells[cell.i][cell.j - 1].curSegment != cell.curSegment) {
                                continue;
                            }
                            cell.newUrban = true;
                            cell.isUrban = true;
                        }
                    }
                }
            } else {
                //Spontaneous
                Random random = new Random();

                ArrayList<Cell> currentSegmentCopy = new ArrayList<>(currentSegment);

                /*currentSegmentCopy.sort(new Comparator<Cell>() {
                    @Override
                    public int compare(Cell o1, Cell o2) {
                        return Double.compare(o1.suitabilityCoefficient, o2.suitabilityCoefficient);
                    }
                });*/

                int nowTriedToChangeToUrban = 0;
                for (int j = 0; j < currentSegmentCopy.size(); j++) {
                    int randId = random.nextInt(currentSegmentCopy.size());
                    if (currentSegmentCopy.get(j).suitabilityCoefficient < 0.05) {//block
                        break;
                    }
                    if (currentSegmentCopy.get(randId).isUrban) {
                        continue;
                    }
                    Cell currentCell = currentSegmentCopy.get(randId);
                    while (nowTriedToChangeToUrban < meanUrbanForPatch) {
                        if (random.nextDouble() <= currentCell.developmentProbability) {
                            currentCell.isUrban = true;
                            currentCell.newUrban = true;
                        }
                        nowTriedToChangeToUrban++;
                        ArrayList<Cell> neighbourhoods = new ArrayList<>();
                        for (int k = -1; k <= 1; k++) {
                            for (int l = -1; l <= 1; l++) {
                                if (0 <= currentCell.i + k && currentCell.i + k < cells.length &&
                                        0 <= currentCell.j + l && currentCell.j + l < cells[0].length &&
                                        cells[currentCell.i + k][currentCell.j + l].land &&
                                        !cells[currentCell.i + k][currentCell.j + l].isUrban &&
                                        cells[currentCell.i + k][currentCell.j + l].curSegment == currentCell.curSegment) {
                                    neighbourhoods.add(cells[currentCell.i + k][currentCell.j + l]);
                                }
                            }
                        }
                        if (neighbourhoods.size() == 0) {
                            break;
                        }

                        currentCell = neighbourhoods.get(IntStream.range(0, neighbourhoods.size())
                                .boxed()
                                .max(Comparator.comparingDouble(id
                                        -> neighbourhoods.get(id).suitabilityCoefficient)).orElse(-1));
                    }
                }
            }
        }
    }


    private ArrayList<ArrayList<Cell>> segmentation(Cell[][] cells) {

        ArrayList<ArrayList<Cell>> segments = new ArrayList<>();
        // 1. Преобразование Cell[][] в Mat
        Mat img = new Mat(cells.length, cells[0].length, CvType.CV_8UC3);
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[0].length; j++) {
                Cell cell = cells[i][j];
                double grayBright = cell.randomForRAFrom0to1 + cell.developmentProbability + cell.neighbourhoodMeanUrban + cell.suitabilityCoefficient;
                grayBright /= 4; //norming
                double val = grayBright * 255;
                double[] values = {val, val, val};
                img.put(i, j, values);
            }
        }

// 2. Фильтрация изображения
        Imgproc.GaussianBlur(img, img, new Size(3, 3), 0);

// 3. Водораздел

        Mat markers = new Mat(img.size(), CvType.CV_32S, new Scalar(0));
        Random rand = new Random();
        int numOFMarkers = (int) Math.pow(cells.length * cells[0].length, degreeOfSegmentation);
        for (int i = 0; i < numOFMarkers; i++) {
            int x = rand.nextInt(img.cols());
            int y = rand.nextInt(img.rows());
            markers.put(y, x, i + 1);
        }

        Imgproc.watershed(img, markers);

// 4. Присвоение значений клеткам
        int cnt = 1;
        HashMap<Integer, Integer> dict = new HashMap<>();
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[0].length; j++) {
                int region = (int) markers.get(i, j)[0];
                if (region == -1) {
                    if (i != 0 && j != 0 && i != cells.length - 1 && j != cells[0].length - 1) {
                        if (markers.get(i + 1, j)[0] > 0) {
                            region = (int) markers.get(i + 1, j)[0];
                        } else if (markers.get(i - 1, j)[0] > 0) {
                            region = (int) markers.get(i - 1, j)[0];
                        } else if (markers.get(i, j + 1)[0] > 0) {
                            region = (int) markers.get(i, j + 1)[0];
                        } else {
                            region = (int) markers.get(i, j - 1)[0];
                        }
                    } else {
                        continue;
                    }
                }
                if (!dict.containsKey(region)) {
                    dict.put(region, cnt++);
                    segments.add(new ArrayList<>());
                }
                segments.get(dict.get(region) - 1).add(cells[i][j]);
                cells[i][j].curSegment = dict.get(region);
            }
        }

        return segments;
    }
}
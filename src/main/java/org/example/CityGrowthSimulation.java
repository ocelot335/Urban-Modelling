package org.example;

import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import net.sf.geographiclib.Geodesic;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.strtree.ItemBoundable;
import org.locationtech.jts.index.strtree.ItemDistance;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

import static java.lang.Double.*;

public class CityGrowthSimulation {

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
    @Getter
    @Setter
    private static int thresholdScore = 7, scoreForFootway = 1, thresholdNumberOfPoints = 5, scoreForPoints = 2;
    private final double[] regressionConstants = {b0, bRD, bHW, bRW, bSW, bMC,
            bDC, bLTC, bSTC};
    private final String[] urbanLandUseTypes = {"residential", "commercial", "industrial",
            "retail", "port", "landfill", "cemetery", "grave_yard", "park", "recreation_groun",
            "playground"};


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
            "\\places.shp",
            "\\railways.shp",
            "\\roads.shp",
            "\\natural.shp",
            "\\points.shp",
            "\\buildings.shp",
            "\\waterways.shp",
            "\\landuse.shp"
    };
    private Map<org.locationtech.jts.geom.Point, Double>[] nearestDistances;
    private double boundsWidth;
    private double boundsHeight;
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;
    private final GeometryFactory geometryFactory; // Used to create new geometries
    @Getter
    private final SimpleFeatureCollection[] shapefilesFeatureCollections;
    private final STRtree[] indexes;
    private final STRtree rivers;
    private final STRtree points;
    private final STRtree buildings;
    private final STRtree waters;
    private final STRtree footways;
    private final STRtree[] landuse = new STRtree[4]; // 0 - commercial, 1 - residential,
    // industrial, 2 - other urban, 3 - else


    public double getBoundsHeight() {
        return boundsHeight;
    }

    public double getBoundsWidth() {
        return boundsWidth;
    }

    public static double getFactor() {
        return FACTOR;
    }

    public CityGrowthSimulation(String path) throws IOException {
        int n = SHAPEFILE_PATHS.length;
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

        indexes = new STRtree[Parameters.values().length];
        // Create an index for the road and city center features to speed up nearest-neighbor queries
        for (int i = 0; i < Parameters.values().length; i++) {
            indexes[i] = new STRtree();
        }
        SimpleFeatureIterator[] iterators = Arrays.stream(shapefilesFeatureCollections).map(SimpleFeatureCollection::features).toArray(SimpleFeatureIterator[]::new);
        footways = new STRtree();
        while (iterators[Shapefiles.ROADS.ordinal()].hasNext()) {
            SimpleFeature roadFeature = iterators[Shapefiles.ROADS.ordinal()].next();
            org.locationtech.jts.geom.MultiLineString roadGeometry = (org.locationtech.jts.geom.MultiLineString) roadFeature.getDefaultGeometry();
            if (roadFeature.getAttribute("maxspeed") != null && (Integer) roadFeature.getAttribute("maxspeed") >= minSpeedForExpressway) {//TODO::>= or >, 60 or 90
                indexes[Parameters.EXPRESS_WAYS.ordinal()].insert(roadGeometry.getEnvelopeInternal(), roadGeometry);
            } else {
                indexes[Parameters.ROADS.ordinal()].insert(roadGeometry.getEnvelopeInternal(), roadGeometry);
            }
            if ("footway".equals(roadFeature.getAttribute("type"))) {
                footways.insert(roadGeometry.getEnvelopeInternal(), roadGeometry);
            }
        }
        while (iterators[Shapefiles.RAILWAYS.ordinal()].hasNext()) {
            SimpleFeature railFeature = iterators[Shapefiles.RAILWAYS.ordinal()].next();
            org.locationtech.jts.geom.MultiLineString railGeometry = (org.locationtech.jts.geom.MultiLineString) railFeature.getDefaultGeometry();
            if ("subway".equals(railFeature.getAttribute("type"))) {
                indexes[Parameters.SUBWAYS.ordinal()].insert(railGeometry.getEnvelopeInternal(), railGeometry);
            } else {
                indexes[Parameters.RAILWAYS.ordinal()].insert(railGeometry.getEnvelopeInternal(), railGeometry);
            }
        }

        while (iterators[Shapefiles.PLACES.ordinal()].hasNext()) {
            SimpleFeature placeFeature = iterators[Shapefiles.PLACES.ordinal()].next();
            org.locationtech.jts.geom.Point placeGeometry = (org.locationtech.jts.geom.Point) placeFeature.getDefaultGeometry();
            String type = (String) placeFeature.getAttribute("type");
            if ("city".equals(type) || "locality".equals(type)) {
                indexes[Parameters.MAIN_CENTERS.ordinal()].insert(placeGeometry.getEnvelopeInternal(), placeGeometry);
            } else if ("quarter".equals(type) || "neighbourhood".equals(type) || "square".equals(type)) {
                indexes[Parameters.DISTRICT_CENTERS.ordinal()].insert(placeGeometry.getEnvelopeInternal(), placeGeometry);
            } else if ("town".equals(type) || "village".equals(type)) {
                indexes[Parameters.LARGE_TOWN_CENTERS.ordinal()].insert(placeGeometry.getEnvelopeInternal(), placeGeometry);
            } else if ("isolated_dwellin".equals(type) || "hamlet".equals(type)) {
                indexes[Parameters.SMALL_TOWN_CENTERS.ordinal()].insert(placeGeometry.getEnvelopeInternal(), placeGeometry);
            }
        }
        waters = new STRtree();
        while (iterators[Shapefiles.NATURAL.ordinal()].hasNext()) {
            SimpleFeature naturalFeature = iterators[Shapefiles.NATURAL.ordinal()].next();
            if ("water".equals(naturalFeature.getAttribute("type"))) {
                Geometry geometry = (Geometry) naturalFeature.getDefaultGeometry();
                waters.insert(geometry.getEnvelopeInternal(), geometry);
            }
        }
        rivers = new STRtree();
        while (iterators[Shapefiles.WATERWAYS.ordinal()].hasNext()) {
            SimpleFeature riverFeature = iterators[Shapefiles.WATERWAYS.ordinal()].next();
            Object geomObj = riverFeature.getDefaultGeometry();
            String type = (String) riverFeature.getAttribute("type");
            if (!"river".equals(type)) {
                continue;
            }
            if (geomObj instanceof MultiLineString riverGeometry) {
                double width = defaultRiverWidth; // значение по умолчанию, если ширина не указана
                if (riverFeature.getAttribute("width") != null) {
                    width = ((Integer) riverFeature.getAttribute("width")).doubleValue(); // получаем ширину реки
                }
                for (int i = 0; i < riverGeometry.getNumGeometries(); i++) {
                    LineString lineString = (LineString) riverGeometry.getGeometryN(i);
                    Geometry bufferedLine = lineString.buffer(width / (2 * FACTOR)); // создаем буфер вдоль линии реки
                    rivers.insert(bufferedLine.getEnvelopeInternal(), bufferedLine); // добавляем объекты в индекс
                }
            }
        }

        points = new STRtree();
        while (iterators[Shapefiles.POINTS.ordinal()].hasNext()) {
            SimpleFeature pointFeature = iterators[Shapefiles.POINTS.ordinal()].next();
            Point point = (Point) pointFeature.getDefaultGeometry();
            points.insert(point.getEnvelopeInternal(), point);
        }

        buildings = new STRtree();
        while (iterators[Shapefiles.BUILDINGS.ordinal()].hasNext()) {
            SimpleFeature buildingFeature = iterators[Shapefiles.BUILDINGS.ordinal()].next();
            MultiPolygon building = (MultiPolygon) buildingFeature.getDefaultGeometry();
            buildings.insert(building.getEnvelopeInternal(), building);
        }

        for (int i = 0; i < landuse.length; i++) {
            landuse[i] = new STRtree();
        }
        while (iterators[Shapefiles.LANDUSE.ordinal()].hasNext()) {
            SimpleFeature landuseFeature = iterators[Shapefiles.LANDUSE.ordinal()].next();
            MultiPolygon landuseGeometry = (MultiPolygon) landuseFeature.getDefaultGeometry();
            String type = (String) landuseFeature.getAttribute("type");
            if ("commercial".equals(type)) {
                landuse[0].insert(landuseGeometry.getEnvelopeInternal(), landuseGeometry);
            } else if ("industrial".equals(type)) {//|| "residential".equals(type)
                landuse[1].insert(landuseGeometry.getEnvelopeInternal(), landuseGeometry);
            } else if (Arrays.asList(urbanLandUseTypes).contains(type)) {
                landuse[2].insert(landuseGeometry.getEnvelopeInternal(), landuseGeometry);
            } else {
                landuse[3].insert(landuseGeometry.getEnvelopeInternal(), landuseGeometry);
            }
        }

        Arrays.stream(iterators).forEach(FeatureIterator::close);

        /*String prjString = null;
        try {
            prjString = Files.readString(Paths.get(path+"\\points.prj"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            CoordinateReferenceSystem crs = CRS.parseWKT(prjString);
        } catch (FactoryException e) {
            throw new RuntimeException(e);
        }*/


        // Initialize the maps of nearest distances
        nearestDistances = new Map[Parameters.values().length];
        for (int i = 0; i < Parameters.values().length; i++) {
            nearestDistances[i] = new HashMap<>();
        }
        // Initialize the geometry factory
        geometryFactory = JTSFactoryFinder.getGeometryFactory();
    }

    private double[] getNearestDistances(org.locationtech.jts.geom.Point point) {
        double[] distances = new double[Parameters.values().length];
        for (int i = 0; i < Parameters.values().length; i++) {
            if (!nearestDistances[i].containsKey(point)) {
                // Find the nearest road by querying the spatial index
                Object nearestFeatures;
                if (i < 4) {
                    nearestFeatures = indexes[i].nearestNeighbour(point.getEnvelopeInternal(), point, new PointLineDistance());
                } else {
                    nearestFeatures = indexes[i].nearestNeighbour(point.getEnvelopeInternal(), point, new PointPointDistance());
                }
                if (nearestFeatures == null) {
                    // There are no roads in the shapefile, so return NaN
                    distances[i] = Double.NaN;
                    continue;
                }
                if (nearestFeatures instanceof org.locationtech.jts.geom.Point) {
                    org.locationtech.jts.geom.Point nearest = (org.locationtech.jts.geom.Point) nearestFeatures;
                    // Calculate the distance from the point to the nearest city center
                    nearestDistances[i].put(point, dist(point, nearest));
                } else if (nearestFeatures instanceof org.locationtech.jts.geom.MultiLineString) {
                    org.locationtech.jts.geom.MultiLineString nearest = (org.locationtech.jts.geom.MultiLineString) nearestFeatures;
                    // Calculate the distance from the point to the nearest road
                    nearestDistances[i].put(point, dist(point, nearest));
                } else {
                    System.err.println("Error with classification of feature");
                    distances[i] = Double.NaN;
                    continue;
                }

            }
            distances[i] = nearestDistances[i].get(point);
        }
        return distances;
    }

    private boolean isInWater(Point point) {
        MultiPolygon nearestWater = (MultiPolygon) waters.nearestNeighbour(point.getEnvelopeInternal(), point, new PointMultiPolygonDistance());
        if (nearestWater.contains(point)) {
            return true;
        }
        List<Geometry> candidates = new ArrayList<>(rivers.query(point.getEnvelopeInternal())); // получаем кандидатов на пересечение
        for (Geometry candidate : candidates) {
            if (candidate.contains(point)) {
                return true;
            }
        }
        return false;
    }

    private void initUrban(Cell cell) {
        int score = 0;

        Point center = cell.point;

        //points | <2 => 0; 2-4 => 1; >4 => 2
        int scoresForPoints = 0;
        Object[] nearestPoints = points.nearestNeighbour(center.getEnvelopeInternal(), center, new PointPointDistance(), thresholdNumberOfPoints);
        for (int i = 0; i < thresholdNumberOfPoints; i++) {
            if (nearestPoints[i] != null && dist(center, (Point) nearestPoints[i]) <= cellSize) {
                if (i == thresholdNumberOfPoints - 1) {
                    scoresForPoints += scoreForPoints;
                }
            } else {
                break;
            }
        }
        score += scoresForPoints;

        //buildings  | 0 => 0; 1 => 1; >1 => 2
        int scoreForBuildings = 0;
        Object[] nearestBuildings = buildings.nearestNeighbour(center.getEnvelopeInternal(), center, new PointMultiPolygonDistance(), 7);
        double S = 0;
        for (int i = 0; i < 7; i++) {
            //double q = dist(center, (MultiPolygon) nearestBuildings[i]);
            if (nearestBuildings[i] == null || dist(center, (MultiPolygon) nearestBuildings[i]) > SCANNING_RADIUS) {
                break;
            }
            if (i == 6) {
                scoreForBuildings += 2;
            }
            S += min(thresholdForAreaForOneBuilding / (FACTOR * FACTOR), ((MultiPolygon) nearestBuildings[i]).getArea());
        }

        if (S >= bigBuildingsArea / (FACTOR * FACTOR)) {
            scoreForBuildings += scoreForBigBuildingsArea;
        } else if (S >= mediumBuildingsArea / (FACTOR * FACTOR)) {
            scoreForBuildings += scoreForMediumBuildingsArea;
        }
        score += scoreForBuildings;


        //landuse | commercial => 4; residential => 3; industrial => 3; other urban => 2; else => 0
        int scoreForLanduse = 0;
        MultiPolygon nearestCommercial = (MultiPolygon) landuse[0].nearestNeighbour(center.getEnvelopeInternal(), center, new PointMultiPolygonDistance());
        if (nearestCommercial != null && nearestCommercial.contains(center)) {
            scoreForLanduse = maxScoreForLanduse;
        } else {
            MultiPolygon nearestResOrInd = (MultiPolygon) landuse[1].nearestNeighbour(center.getEnvelopeInternal(), center, new PointMultiPolygonDistance());
            if ((nearestResOrInd != null && nearestResOrInd.contains(center)) ||
                    (nearestCommercial != null && dist(center, nearestCommercial) <= SCANNING_RADIUS)) {
                scoreForLanduse = maxScoreForLanduse - 1;
            } else {
                MultiPolygon nearestOtherUrban = (MultiPolygon) landuse[2].nearestNeighbour(center.getEnvelopeInternal(), center, new PointMultiPolygonDistance());
                if (nearestOtherUrban != null && nearestOtherUrban.contains(center)) {
                    scoreForLanduse = Math.max(maxScoreForLanduse - 2, 0);
                } else if ((nearestOtherUrban != null && dist(center, nearestOtherUrban) <= SCANNING_RADIUS) ||
                        (nearestResOrInd != null && dist(center, nearestResOrInd) <= SCANNING_RADIUS)) {
                    scoreForLanduse = Math.max(maxScoreForLanduse - 3, 0);
                }
            }
        }
        score += scoreForLanduse;
        //footway | exist => 2; else => 0
        int scoresForFootways = 0;
        Object nearestFootway = footways.nearestNeighbour(center.getEnvelopeInternal(), center, new PointLineDistance());
        if (nearestFootway != null && dist(center, (MultiLineString) nearestFootway) <= SCANNING_RADIUS) {
            scoresForFootways += scoreForFootway;
        }
        score += scoresForFootways;

        if (score >= thresholdScore) {//7 - best
            cell.isUrban = true;
        } else {
            cell.isUrban = false;
        }
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
                        minY + j * cellSize + cellSize / 2, i, j, getNearestDistances(centroid), isInWater(centroid), centroid);
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

    class Cell {
        final public double x;
        final public double y;
        final public int i;
        final public int j;
        final public Point point;
        final public double suitabilityCoefficient;
        final public double[] nearestDistances;

        double developmentProbability;

        double neighbourhoodMeanUrban;
        double RA;
        double randomForRAFrom0to1;
        boolean land;
        boolean newUrban = false;
        boolean isUrban;
        int curSegment;
        boolean test = false;

        public Cell(double x, double y, int i, int j, double[] nearestDistances, boolean isInWater, Point centroid) {
            this.x = x;
            this.y = y;
            this.i = i;
            this.j = j;
            this.nearestDistances = nearestDistances;
            land = !isInWater;
            double zValue = 0;
            for (int k = 0; k < Parameters.values().length; k++) {
                if (isNaN(nearestDistances[k])) {
                    zValue += regressionConstants[k + 1] * min(boundsHeight, boundsWidth) * FACTOR / (2) * kConst;
                } else {
                    zValue += regressionConstants[k + 1] * nearestDistances[k];
                }
            }
            zValue *= kConst;
            zValue += regressionConstants[0];
            BigDecimal exp = new BigDecimal(Math.exp(zValue));
            BigDecimal denominator = exp.add(BigDecimal.ONE);
            BigDecimal result = exp.divide(denominator, RoundingMode.HALF_UP);
            suitabilityCoefficient = result.doubleValue();
            point = centroid;
            initUrban(this);
        }

        public void setRA() {
            randomForRAFrom0to1 = Math.random(); // генерация случайного числа от 0 до 1
            RA = 1 + Math.pow(-Math.log(randomForRAFrom0to1), degreeOfPerturbation);
        }
    }

    private class PointLineDistance implements ItemDistance {
        @Override
        public double distance(ItemBoundable item1, ItemBoundable item2) {
            org.locationtech.jts.geom.MultiLineString line = (org.locationtech.jts.geom.MultiLineString) item1.getItem();
            Point point = (Point) (item2.getItem());
            return point.distance(line);
        }
    }

    private class PointPointDistance implements ItemDistance {
        @Override
        public double distance(ItemBoundable item1, ItemBoundable item2) {
            org.locationtech.jts.geom.Point point1 = (org.locationtech.jts.geom.Point) item1.getItem();
            Point point2 = (Point) (item2.getItem());
            return point2.distance(point1);
        }
    }

    private class PointMultiPolygonDistance implements ItemDistance {
        @Override
        public double distance(ItemBoundable item1, ItemBoundable item2) {
            org.locationtech.jts.geom.MultiPolygon multiPolygon = (org.locationtech.jts.geom.MultiPolygon) item1.getItem();
            Point point = (Point) (item2.getItem());
            return point.distance(multiPolygon);
        }
    }

    public static double dist(Geometry g1, Geometry g2) {
        Coordinate[] coords = new DistanceOp(g1, g2).nearestPoints();
        return Geodesic.WGS84.Inverse(coords[0].x, coords[0].y, coords[1].x, coords[1].y).s12;
    }
}
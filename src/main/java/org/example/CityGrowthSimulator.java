package org.example;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.*;
import org.geotools.geometry.jts.*;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.strtree.ItemBoundable;
import org.locationtech.jts.index.strtree.ItemDistance;
import org.locationtech.jts.index.strtree.STRtree;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory2;
import org.opengis.geometry.DirectPosition;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.crs.*;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Polygon;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CityGrowthSimulator {
    private final static double CELL_SIZE = 100; // The size of each cell in meters
    private final static String ROAD_LAYER_NAME = "roads"; // The name of the roads layer in the shapefile
    private final static String CITY_CENTER_LAYER_NAME = "city_centers"; // The name of the city centers layer in the shapefile

    private static final String[] SHAPEFILE_PATHS = {
            "buildings.shp",
            "landuse.shp",
            "natural.shp",
            "places.shp",
            "points.shp",
            "railways.shp",
            "roads.shp",
            "waterways.shp"
    };
    private SimpleFeatureCollection roadFeatures; // The features in the roads layer
    private SimpleFeatureCollection cityCenterFeatures; // The features in the city centers layer
    private Map<org.locationtech.jts.geom.Point, Double> nearestRoadDistances; // The map of nearest road distances for each point
    private Map<org.locationtech.jts.geom.Point, Double> nearestCityCenterDistances; // The map of nearest city center distances for each point
    private GeometryFactory geometryFactory; // Used to create new geometries
    private SimpleFeatureCollection[] featureCollections;
    private STRtree roadIndex;
    private STRtree cityCenterIndex;

    public CityGrowthSimulator() {
        int n = SHAPEFILE_PATHS.length;
        featureCollections = new SimpleFeatureCollection[n];
        for (int i = 0; i < n; i++) {
            File shapefile = new File(SHAPEFILE_PATHS[i]);
            ShapefileDataStore dataStore = null;
            try {
                dataStore = new ShapefileDataStore(shapefile.toURI().toURL());
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Get the names of all the layers in the shapefile
            String[] typeNames = new String[0];
            try {
                typeNames = dataStore.getTypeNames();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Load the features
            try {
                featureCollections[i] = dataStore.getFeatureSource(typeNames[0]).getFeatures();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Load the road and city center layers
        roadFeatures = featureCollections[6];
        cityCenterFeatures = featureCollections[3];
        // Create an index for the road and city center features to speed up nearest-neighbor queries
        roadIndex = new STRtree();
        cityCenterIndex = new STRtree();
        SimpleFeatureIterator roadIterator = roadFeatures.features();
        SimpleFeatureIterator cityCenterIterator = cityCenterFeatures.features();
        while (roadIterator.hasNext()) {
            SimpleFeature roadFeature = roadIterator.next();
            org.locationtech.jts.geom.LineString roadGeometry = (org.locationtech.jts.geom.LineString) roadFeature.getDefaultGeometry();
            roadIndex.insert(roadGeometry.getEnvelopeInternal(), roadGeometry);
        }
        while (cityCenterIterator.hasNext()) {
            SimpleFeature cityCenterFeature = cityCenterIterator.next();
            org.locationtech.jts.geom.Point cityCenterGeometry = (org.locationtech.jts.geom.Point) cityCenterFeature.getDefaultGeometry();
            cityCenterIndex.insert(cityCenterGeometry.getEnvelopeInternal(), cityCenterGeometry);
        }
        roadIterator.close();
        cityCenterIterator.close();
        // Initialize the maps of nearest distances
        nearestRoadDistances = new HashMap<>();
        nearestCityCenterDistances = new HashMap<>();
        // Initialize the geometry factory
        geometryFactory = JTSFactoryFinder.getGeometryFactory();
    }

    // Returns the distance from a point to the nearest road
    private double getNearestRoadDistance(org.locationtech.jts.geom.Point point) {
        if (!nearestRoadDistances.containsKey(point)) {
            // Find the nearest road by querying the spatial index
            Object nearestFeatures = roadIndex.nearestNeighbour(point.getEnvelopeInternal(), point, new PointDistance());
            if (nearestFeatures == null) {
                // There are no roads in the shapefile, so return NaN
                return Double.NaN;
            }
            org.locationtech.jts.geom.LineString nearestRoad = (org.locationtech.jts.geom.LineString) nearestFeatures;
            // Calculate the distance from the point to the nearest road
            nearestRoadDistances.put(point, point.distance(nearestRoad));
        }
        return nearestRoadDistances.get(point);
    }

    // Returns the distance from a point to the nearest city center
    private double getNearestCityCenterDistance(org.locationtech.jts.geom.Point point) {
        if (!nearestCityCenterDistances.containsKey(point)) {
            // Find the nearest city center by querying the spatial index
            Object nearestFeatures = cityCenterIndex.nearestNeighbour(point.getEnvelopeInternal(), point, new PointDistance());
            if (nearestFeatures == null) {
                // There are no city centers in the shapefile, so return NaN
                return Double.NaN;
            }
            org.locationtech.jts.geom.Point nearestCityCenter = (org.locationtech.jts.geom.Point) nearestFeatures;
            // Calculate the distance from the point to the nearest city center
            nearestCityCenterDistances.put(point, point.distance(nearestCityCenter));
        }
        return nearestCityCenterDistances.get(point);
    }

    public void simulateGrowth(Polygon cityBounds, int iterations) {
        // Calculate the number of cells in the grid
        double boundsWidth = cityBounds.getEnvelopeInternal().getWidth();
        double boundsHeight = cityBounds.getEnvelopeInternal().getHeight();
        int numCellsX = (int) Math.ceil(boundsWidth / CELL_SIZE);
        int numCellsY = (int) Math.ceil(boundsHeight / CELL_SIZE);
        // Create a 2D array to hold the cells
        Cell[][] cells = new Cell[numCellsX][numCellsY];
        // Initialize the cells with their centroid coordinates and the nearest road and city center distances
        for (int i = 0; i < numCellsX; i++) {
            for (int j = 0; j < numCellsY; j++) {
                // Calculate the coordinates of the centroid of this cell
                double x = cityBounds.getEnvelopeInternal().getMinX() + i * CELL_SIZE + CELL_SIZE / 2;
                double y = cityBounds.getEnvelopeInternal().getMinY() + j * CELL_SIZE + CELL_SIZE / 2;
                Point centroid = geometryFactory.createPoint(new Coordinate(x, y));
                // Calculate the nearest road and city center distances for this cell
                double nearestRoadDistance = getNearestRoadDistance(centroid);
                double nearestCityCenterDistance = getNearestCityCenterDistance(centroid);
                // Create a new cell object and store it in the array
                cells[i][j] = new Cell(centroid.getX(), centroid.getY(), nearestRoadDistance, nearestCityCenterDistance);
            }
        }
        // Run the simulation for the specified number of iterations
        for (int iter = 0; iter < iterations; iter++) {
            // TODO: Implement the growth simulation step here
        }
    }

    private class Cell {
        public double x;
        public double y;
        public double nearestRoadDistance;
        public double nearestCityCenterDistance;

        public Cell(double x, double y, double nearestRoadDistance, double nearestCityCenterDistance) {
            this.x = x;
            this.y = y;
            this.nearestRoadDistance = nearestRoadDistance;
            this.nearestCityCenterDistance = nearestCityCenterDistance;
        }
    }

    class PointDistance implements ItemDistance {
        @Override
        public double distance(ItemBoundable item1, ItemBoundable item2) {
            org.locationtech.jts.geom.LineString line = (org.locationtech.jts.geom.LineString) item1.getItem();
            Point point = (Point) (item2.getItem());
            return point.distance(line);
        }
    }
}
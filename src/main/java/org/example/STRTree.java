package org.example;

import lombok.Data;
import net.sf.geographiclib.Geodesic;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.FeatureIterator;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.ItemBoundable;
import org.locationtech.jts.index.strtree.ItemDistance;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.opengis.feature.simple.SimpleFeature;

import java.util.*;

@Data
public class STRTree {
    private STRtree[] indexes;
    private STRtree rivers;
    private STRtree points;
    private STRtree buildings;
    private STRtree waters;
    private STRtree footways;
    private STRtree[] landuse = new STRtree[4];// 0 - commercial, 1 - residential,
    // industrial, 2 - other urban, 3 - else
    private Map<Point, Double>[] nearestDistances;

    private final String[] urbanLandUseTypes = {"residential", "commercial", "industrial",
            "retail", "port", "landfill", "cemetery", "grave_yard", "park", "recreation_groun",
            "playground"};

    public STRTree(SimpleFeatureCollection[] shapefilesFeatureCollections, int minSpeedForExpressway, double defaultRiverWidth,
                   double FACTOR) {

        indexes = new STRtree[Simulation.Parameters.values().length];
        // Create an index for the road and city center features to speed up nearest-neighbor queries
        for (int i = 0; i < Simulation.Parameters.values().length; i++) {
            indexes[i] = new STRtree();
        }
        SimpleFeatureIterator[] iterators = Arrays.stream(shapefilesFeatureCollections).map(SimpleFeatureCollection::features).toArray(SimpleFeatureIterator[]::new);
        footways = new STRtree();
        while (iterators[Simulation.Shapefiles.ROADS.ordinal()].hasNext()) {
            SimpleFeature roadFeature = iterators[Simulation.Shapefiles.ROADS.ordinal()].next();
            org.locationtech.jts.geom.MultiLineString roadGeometry = (org.locationtech.jts.geom.MultiLineString) roadFeature.getDefaultGeometry();
            if (roadFeature.getAttribute("maxspeed") != null && (Integer) roadFeature.getAttribute("maxspeed") >= minSpeedForExpressway) {//TODO::>= or >, 60 or 90
                indexes[Simulation.Parameters.EXPRESS_WAYS.ordinal()].insert(roadGeometry.getEnvelopeInternal(), roadGeometry);
            } else {
                indexes[Simulation.Parameters.ROADS.ordinal()].insert(roadGeometry.getEnvelopeInternal(), roadGeometry);
            }
            if ("footway".equals(roadFeature.getAttribute("type"))) {
                footways.insert(roadGeometry.getEnvelopeInternal(), roadGeometry);
            }
        }
        while (iterators[Simulation.Shapefiles.RAILWAYS.ordinal()].hasNext()) {
            SimpleFeature railFeature = iterators[Simulation.Shapefiles.RAILWAYS.ordinal()].next();
            org.locationtech.jts.geom.MultiLineString railGeometry = (org.locationtech.jts.geom.MultiLineString) railFeature.getDefaultGeometry();
            if ("subway".equals(railFeature.getAttribute("type"))) {
                indexes[Simulation.Parameters.SUBWAYS.ordinal()].insert(railGeometry.getEnvelopeInternal(), railGeometry);
            } else {
                indexes[Simulation.Parameters.RAILWAYS.ordinal()].insert(railGeometry.getEnvelopeInternal(), railGeometry);
            }
        }

        while (iterators[Simulation.Shapefiles.PLACES.ordinal()].hasNext()) {
            SimpleFeature placeFeature = iterators[Simulation.Shapefiles.PLACES.ordinal()].next();
            org.locationtech.jts.geom.Point placeGeometry = (org.locationtech.jts.geom.Point) placeFeature.getDefaultGeometry();
            String type = (String) placeFeature.getAttribute("type");
            if ("city".equals(type) || "locality".equals(type)) {
                indexes[Simulation.Parameters.MAIN_CENTERS.ordinal()].insert(placeGeometry.getEnvelopeInternal(), placeGeometry);
            } else if ("quarter".equals(type) || "neighbourhood".equals(type) || "square".equals(type)) {
                indexes[Simulation.Parameters.DISTRICT_CENTERS.ordinal()].insert(placeGeometry.getEnvelopeInternal(), placeGeometry);
            } else if ("town".equals(type) || "village".equals(type)) {
                indexes[Simulation.Parameters.LARGE_TOWN_CENTERS.ordinal()].insert(placeGeometry.getEnvelopeInternal(), placeGeometry);
            } else if ("isolated_dwellin".equals(type) || "hamlet".equals(type)) {
                indexes[Simulation.Parameters.SMALL_TOWN_CENTERS.ordinal()].insert(placeGeometry.getEnvelopeInternal(), placeGeometry);
            }
        }
        waters = new STRtree();
        while (iterators[Simulation.Shapefiles.NATURAL.ordinal()].hasNext()) {
            SimpleFeature naturalFeature = iterators[Simulation.Shapefiles.NATURAL.ordinal()].next();
            if ("water".equals(naturalFeature.getAttribute("type"))) {
                Geometry geometry = (Geometry) naturalFeature.getDefaultGeometry();
                waters.insert(geometry.getEnvelopeInternal(), geometry);
            }
        }
        rivers = new STRtree();
        while (iterators[Simulation.Shapefiles.WATERWAYS.ordinal()].hasNext()) {
            SimpleFeature riverFeature = iterators[Simulation.Shapefiles.WATERWAYS.ordinal()].next();
            Object geomObj = riverFeature.getDefaultGeometry();
            String type = (String) riverFeature.getAttribute("type");
            if (!"river".equals(type)) {
                continue;
            }
            if (geomObj instanceof MultiLineString) {
                MultiLineString riverGeometry = (MultiLineString) geomObj;
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
        while (iterators[Simulation.Shapefiles.POINTS.ordinal()].hasNext()) {
            SimpleFeature pointFeature = iterators[Simulation.Shapefiles.POINTS.ordinal()].next();
            Point point = (Point) pointFeature.getDefaultGeometry();
            points.insert(point.getEnvelopeInternal(), point);
        }

        buildings = new STRtree();
        while (iterators[Simulation.Shapefiles.BUILDINGS.ordinal()].hasNext()) {
            SimpleFeature buildingFeature = iterators[Simulation.Shapefiles.BUILDINGS.ordinal()].next();
            MultiPolygon building = (MultiPolygon) buildingFeature.getDefaultGeometry();
            buildings.insert(building.getEnvelopeInternal(), building);
        }

        for (int i = 0; i < landuse.length; i++) {
            landuse[i] = new STRtree();
        }
        while (iterators[Simulation.Shapefiles.LANDUSE.ordinal()].hasNext()) {
            SimpleFeature landuseFeature = iterators[Simulation.Shapefiles.LANDUSE.ordinal()].next();
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


        // Initialize the maps of nearest distances
        nearestDistances = new Map[Simulation.Parameters.values().length];
        for (int i = 0; i < Simulation.Parameters.values().length; i++) {
            nearestDistances[i] = new HashMap<>();
        }
    }

    public double dist(Geometry g1, Geometry g2) {
        Coordinate[] coords = new DistanceOp(g1, g2).nearestPoints();
        return Geodesic.WGS84.Inverse(coords[0].x, coords[0].y, coords[1].x, coords[1].y).s12;
    }

    public static class PointLineDistance implements ItemDistance {
        @Override
        public double distance(ItemBoundable item1, ItemBoundable item2) {
            org.locationtech.jts.geom.MultiLineString line = (org.locationtech.jts.geom.MultiLineString) item1.getItem();
            Point point = (Point) (item2.getItem());
            return point.distance(line);
        }
    }

    public static class PointPointDistance implements ItemDistance {
        @Override
        public double distance(ItemBoundable item1, ItemBoundable item2) {
            org.locationtech.jts.geom.Point point1 = (org.locationtech.jts.geom.Point) item1.getItem();
            Point point2 = (Point) (item2.getItem());
            return point2.distance(point1);
        }
    }

    public static class PointMultiPolygonDistance implements ItemDistance {
        @Override
        public double distance(ItemBoundable item1, ItemBoundable item2) {
            org.locationtech.jts.geom.MultiPolygon multiPolygon = (org.locationtech.jts.geom.MultiPolygon) item1.getItem();
            Point point = (Point) (item2.getItem());
            return point.distance(multiPolygon);
        }
    }

    public boolean isInWater(Point point) {
        MultiPolygon nearestWater = (MultiPolygon) waters.nearestNeighbour(point.getEnvelopeInternal(), point, new STRTree.PointMultiPolygonDistance());
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

    public double[] getNearestDistances(org.locationtech.jts.geom.Point point) {
        double[] distances = new double[Simulation.Parameters.values().length];
        for (int i = 0; i < Simulation.Parameters.values().length; i++) {
            if (!nearestDistances[i].containsKey(point)) {
                // Find the nearest road by querying the spatial index
                Object nearestFeatures;
                if (i < 4) {
                    nearestFeatures = indexes[i].nearestNeighbour(point.getEnvelopeInternal(), point, new STRTree.PointLineDistance());
                } else {
                    nearestFeatures = indexes[i].nearestNeighbour(point.getEnvelopeInternal(), point, new STRTree.PointPointDistance());
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
}

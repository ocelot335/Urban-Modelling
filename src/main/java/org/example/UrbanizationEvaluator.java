package org.example;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;

import java.util.function.Function;

import static java.lang.Double.min;

@AllArgsConstructor
public class UrbanizationEvaluator {
    double cellSize;
    int thresholdNumberOfPoints;
    int scoreForPoints;
    int maxScoreForLanduse;
    int scoreForBigBuildingsArea;
    int scoreForMediumBuildingsArea;
    int scoreForFootway;
    int thresholdScore;
    double SCANNING_RADIUS;
    double FACTOR;
    double thresholdForAreaForOneBuilding;
    double bigBuildingsArea;
    double mediumBuildingsArea;
    STRTree strTree;

    public void initUrban(Cell cell) {
        int score = 0;

        Point center = cell.point;

        //points | <2 => 0; 2-4 => 1; >4 => 2
        int scoresForPoints = 0;
        Object[] nearestPoints = strTree.getPoints().nearestNeighbour(center.getEnvelopeInternal(), center, new STRTree.PointPointDistance(), thresholdNumberOfPoints);
        for (int i = 0; i < thresholdNumberOfPoints; i++) {
            if (nearestPoints[i] != null && strTree.dist(center, (Point) nearestPoints[i]) <= cellSize) {
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
        Object[] nearestBuildings = strTree.getBuildings().nearestNeighbour(center.getEnvelopeInternal(), center, new STRTree.PointMultiPolygonDistance(), 7);
        double S = 0;
        for (int i = 0; i < 7; i++) {
            //double q = dist(center, (MultiPolygon) nearestBuildings[i]);
            if (nearestBuildings[i] == null || strTree.dist(center, (MultiPolygon) nearestBuildings[i]) > SCANNING_RADIUS) {
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
        MultiPolygon nearestCommercial = (MultiPolygon) strTree.getLanduse()[0].nearestNeighbour(center.getEnvelopeInternal(), center, new STRTree.PointMultiPolygonDistance());
        if (nearestCommercial != null && nearestCommercial.contains(center)) {
            scoreForLanduse = maxScoreForLanduse;
        } else {
            MultiPolygon nearestResOrInd = (MultiPolygon) strTree.getLanduse()[1].nearestNeighbour(center.getEnvelopeInternal(), center, new STRTree.PointMultiPolygonDistance());
            if ((nearestResOrInd != null && nearestResOrInd.contains(center)) ||
                    (nearestCommercial != null && strTree.dist(center, nearestCommercial) <= SCANNING_RADIUS)) {
                scoreForLanduse = maxScoreForLanduse - 1;
            } else {
                MultiPolygon nearestOtherUrban = (MultiPolygon) strTree.getLanduse()[2].nearestNeighbour(center.getEnvelopeInternal(), center, new STRTree.PointMultiPolygonDistance());
                if (nearestOtherUrban != null && nearestOtherUrban.contains(center)) {
                    scoreForLanduse = Math.max(maxScoreForLanduse - 2, 0);
                } else if ((nearestOtherUrban != null && strTree.dist(center, nearestOtherUrban) <= SCANNING_RADIUS) ||
                        (nearestResOrInd != null && strTree.dist(center, nearestResOrInd) <= SCANNING_RADIUS)) {
                    scoreForLanduse = Math.max(maxScoreForLanduse - 3, 0);
                }
            }
        }
        score += scoreForLanduse;
        //footway | exist => 2; else => 0
        int scoresForFootways = 0;
        Object nearestFootway = strTree.getFootways().nearestNeighbour(center.getEnvelopeInternal(), center, new STRTree.PointLineDistance());
        if (nearestFootway != null && strTree.dist(center, (MultiLineString) nearestFootway) <= SCANNING_RADIUS) {
            scoresForFootways += scoreForFootway;
        }
        score += scoresForFootways;

        if (score >= thresholdScore) {//7 - best
            cell.isUrban = true;
            cell.newUrbanAt = -1;
        } else {
            cell.isUrban = false;
        }
    }

}

package org.example;

import lombok.Data;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static java.lang.Double.isNaN;

@Data
public class Cell {
    final public double x;
    final public double y;
    final public int i;
    final public int j;
    final public Point point;
    final public double suitabilityCoefficient;
    final public double[] nearestDistances;

    private final double[] regressionConstants;

    double developmentProbability;

    double neighbourhoodMeanUrban;
    double RA;
    double randomForRAFrom0to1;
    boolean land;
    boolean newUrban = false;
    boolean isUrban;
    int curSegment;
    double degreeOfPerturbation;
    boolean test = false;

    public Cell(double x, double y, int i, int j, double[] nearestDistances, boolean isInWater, Point centroid,
                double[] regressionConstants, double degreeOfPerturbation, double kConst, double factor) {
        //factor = min(boundsHeight, boundsWidth) * FACTOR / (2)
        this.regressionConstants = regressionConstants;
        this.x = x;
        this.y = y;
        this.i = i;
        this.j = j;
        this.degreeOfPerturbation = degreeOfPerturbation;
        this.nearestDistances = nearestDistances;
        land = !isInWater;
        double zValue = 0;
        for (int k = 0; k < Simulation.Parameters.values().length; k++) {
            if (isNaN(nearestDistances[k])) {
                zValue += regressionConstants[k + 1] * factor * kConst;
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
    }

    public void setRA() {
        randomForRAFrom0to1 = Math.random(); // генерация случайного числа от 0 до 1
        RA = 1 + Math.pow(-Math.log(randomForRAFrom0to1), degreeOfPerturbation);
    }
}

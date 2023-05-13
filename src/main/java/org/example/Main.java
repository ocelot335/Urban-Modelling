package org.example;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

public class Main{

    public static void main(String[] args) {
        Application.launch(CityGrowthApplication.class);
    }
}


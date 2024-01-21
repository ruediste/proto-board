package com.github.ruediste;

import static com.github.ruediste.Vector.vector;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class GerberWriter implements AutoCloseable {

    private PrintStream out;
    private int nextApertureNr = 10;

    private HashMap<List<Object>, Aperture> apertureCache = new HashMap<>();
    private boolean currentPolarityIsDark;

    public GerberWriter(UUID ident, String outputFile) throws FileNotFoundException {
        this.out = new PrintStream(new FileOutputStream(outputFile, false), true, StandardCharsets.UTF_8);
        this.out.println("%TF.SameCoordinates," + ident + "*%");
    }

    public GerberWriter(UUID ident, String outputFile, String baseFile) throws IOException {
        this.out = new PrintStream(new FileOutputStream(outputFile, false), true, StandardCharsets.UTF_8);

        // Set aperture to a high number. Hacky, but avoids the need to parse the file.
        nextApertureNr = 1000;

        // copy base file except for the termination
        try (var reader = new BufferedReader(new FileReader(baseFile, StandardCharsets.UTF_8))) {
            while (true) {
                var line = reader.readLine();
                if (line == null || line.equals("M02*"))
                    break;
                out.println(line);
            }
        }
    }

    public void fileAttributesFinished() {
        this.out.println("%FSLAX46Y46*%"); // format of coordinates
        this.out.println("%MOMM*%"); // metric millimeters
        currentPolarityIsDark = false;
        this.polarityDark();
    }

    @Override
    public void close() throws Exception {
        out.println("M02*");// end of file
        out.close();
    }

    public GerberWriter polarityDark() {
        if (!currentPolarityIsDark) {
            out.println("%LPD*%");
            currentPolarityIsDark = true;
        }
        return this;
    }

    public GerberWriter polarityClear() {
        if (currentPolarityIsDark) {
            out.println("%LPC*%");
            currentPolarityIsDark = false;
        }
        return this;
    }

    public GerberWriter contour(Runnable r) {
        out.println("G36*");
        r.run();
        out.println("G37*");
        return this;
    }

    public record ApertureArgs(String function) {
    }

    private void handleApertureArgs(ApertureArgs args) {
        if (args == null)
            return;

        if (args.function != null) {
            out.printf("%%TA.AperFunction,%s*%%\n", args.function);
        }
    }

    public GerberWriter apertureCircle(double diameter) {
        return apertureCircle(diameter, new ApertureArgs(null));
    }

    public GerberWriter apertureCircle(double diameter, ApertureArgs args) {
        return setCurrentAperture(apertureCache.computeIfAbsent(List.of("circle", diameter, args), k -> {
            handleApertureArgs(args);
            out.printf("%%ADD%dC,%f*%%\n", nextApertureNr, diameter);
            out.println("%TD*%");
            return new Aperture(nextApertureNr++);
        }));
    }

    public GerberWriter apertureRectangle(double xSize, double ySize) {
        return apertureRectangle(xSize, ySize, new ApertureArgs(null));
    }

    public GerberWriter apertureRectangle(double xSize, double ySize, ApertureArgs args) {
        return setCurrentAperture(apertureCache.computeIfAbsent(List.of("rectangle", xSize, ySize, args), k -> {
            handleApertureArgs(args);
            out.printf("%%ADD%dR,%fX%f*%%\n", nextApertureNr, xSize, ySize);
            return new Aperture(nextApertureNr++);
        }));
    }

    public Aperture startBlockAperture() {
        var result = new Aperture(nextApertureNr++);
        out.printf("%%ABD%d*%%\n", result.nr);
        return result;
    }

    public GerberWriter endBlockAperture() {
        out.println("%AB*%");
        return this;
    }

    private Integer currentApertureNr;

    public GerberWriter setCurrentAperture(Aperture aperture) {
        if (currentApertureNr == null || currentApertureNr != aperture.nr) {
            out.printf("D%d*\n", aperture.nr);
            currentApertureNr = aperture.nr;
        }
        return this;
    }

    private String formatCoordinate(double value) {
        return String.format("%09d", (int) (value * 1e6));
    }

    public GerberWriter move(Vector v) {
        return move(v.x, v.y);
    }

    public GerberWriter move(double x, double y) {
        out.printf("X%sY%sD02*\n", formatCoordinate(x), formatCoordinate(y));
        return this;
    }

    public GerberWriter linearInterpolation() {
        out.println("G01*");
        return this;
    }

    public enum CopperLayerType {
        Top("Top"), Inner("Inr"), Bottom("Bot");

        private String value;

        private CopperLayerType(String value) {
            this.value = value;
        }
    }

    public GerberWriter attrFileFunctionCopper(int layer, CopperLayerType type) {
        out.printf("%%TF.FileFunction,Copper,L%d,%s*%%\n", layer, type.value);
        return this;
    }

    public GerberWriter attrFileFunctionPlated(int from, int to) {
        out.printf("%%TF.FileFunction,Plated,%d,%d,PTH,Drill*%%\n", from, to);
        return this;
    }

    public GerberWriter attrFileFunctionSoldermask(boolean top) {
        out.printf("%%TF.FileFunction,Soldermask,%s*%%\n", top ? "Top" : "Bot");
        return this;
    }

    public GerberWriter attrFileFunctionLegend(boolean top) {
        out.printf("%%TF.FileFunction,Legend,%s*%%\n", top ? "Top" : "Bot");
        return this;
    }

    public GerberWriter attrFilePolarity(boolean positive) {
        out.printf("%%TF.FilePolarity,%s*%%\n", positive ? "Positive" : "Negative");
        return this;
    }

    public GerberWriter rectangle(Vector center, double xSize, double ySize, double angleDegrees) {
        return contour(() -> {
            move(vector(-xSize / 2, -ySize / 2).rotate(angleDegrees).add(center));
            linearInterpolation();
            interpolate(vector(xSize / 2, -ySize / 2).rotate(angleDegrees).add(center));
            interpolate(vector(xSize / 2, ySize / 2).rotate(angleDegrees).add(center));
            interpolate(vector(-xSize / 2, ySize / 2).rotate(angleDegrees).add(center));
        });
    }

    public GerberWriter interpolate(Vector v) {
        return interpolate(v.x, v.y);
    }

    public GerberWriter interpolate(double x, double y) {
        out.printf("X%sY%sD01*\n", formatCoordinate(x), formatCoordinate(y));
        return this;
    }

    public static class Aperture {
        public int nr;

        public Aperture(int nr) {
            this.nr = nr;
        }
    }

    public GerberWriter flash(double x, double y) {
        out.printf("X%sY%sD03*\n", formatCoordinate(x), formatCoordinate(y));
        return this;
    }

    public GerberWriter loadRotation(double angle) {
        out.printf("%%LR%.1f*%%\n", angle);
        return this;
    }

}

package com.github.ruediste;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import com.github.ruediste.GerberWriter.ApertureArgs;
import com.github.ruediste.GerberWriter.CopperLayerType;

import static com.github.ruediste.Vector.vector;

/**
 * Hello world!
 *
 */
public class App {
    public static void main(String[] args) throws FileNotFoundException, Exception {
        new App().run();
    }

    List<Runnable> queue = new ArrayList<>();

    GerberWriter top;
    GerberWriter topMask;
    GerberWriter topSilk;
    GerberWriter in1;
    GerberWriter in2;
    GerberWriter bottom;
    GerberWriter bottomMask;
    GerberWriter bottomSilk;
    GerberWriter pth;
    GerberWriter edgeCuts;

    public int layerCount = 4;
    List<GerberWriter> allLayers;
    double soldermaskExpansion = 0.038;
    double raster = 2.54;

    public GerberWriter copperLayer(int layerNr) {
        switch (layerNr) {
            case 0:
                return top;
            case 1:
                return in1;
            case 2:
                return in2;
            case 3:
                return bottom;
            default:
                throw new IllegalArgumentException();
        }
    }

    public GerberWriter maskLayer(int layerNr) {
        switch (layerNr) {
            case 0:
                return topMask;
            case 3:
                return bottomMask;
            default:
                throw new IllegalArgumentException();
        }
    }

    public GerberWriter silkLayer(int layerNr) {
        switch (layerNr) {
            case 0:
                return topSilk;
            case 3:
                return bottomSilk;
            default:
                throw new IllegalArgumentException();
        }
    }

    UUID ident = UUID.randomUUID();
    String prefix = "protoboard-";
    boolean append = true;

    private GerberWriter openWriter(String suffix) throws IOException {
        return new GerberWriter(ident, prefix + suffix, "base/" + prefix + suffix);
    }

    public void run() throws Exception {
        top = openWriter("F_Cu.gbr");
        topMask = openWriter("F_Mask.gbr");
        topSilk = openWriter("F_Silkscreen.gbr");
        in1 = openWriter("In1_Cu.gbr");
        in2 = openWriter("In2_Cu.gbr");
        bottom = openWriter("B_Cu.gbr");
        bottomMask = openWriter("B_Mask.gbr");
        bottomSilk = openWriter("B_Silkscreen.gbr");
        pth = openWriter("PTH-drl.gbr");
        edgeCuts = openWriter("Edge_Cuts.gbr");

        allLayers = List.of(top, topMask, topSilk, in1, in2, bottom, bottomMask, bottomSilk, pth, edgeCuts);

        try {
            if (!append) {
                top.attrFileFunctionCopper(1, CopperLayerType.Top);
                top.attrFilePolarity(true);
                in1.attrFileFunctionCopper(2, CopperLayerType.Inner);
                in1.attrFilePolarity(true);
                in2.attrFileFunctionCopper(3, CopperLayerType.Inner);
                in2.attrFilePolarity(true);
                bottom.attrFileFunctionCopper(4, CopperLayerType.Bottom);
                bottom.attrFilePolarity(true);

                topMask.attrFileFunctionSoldermask(true);
                topMask.attrFilePolarity(false);
                bottomMask.attrFileFunctionSoldermask(false);
                bottomMask.attrFilePolarity(false);

                topSilk.attrFileFunctionLegend(true);
                topSilk.attrFilePolarity(true);
                bottomSilk.attrFileFunctionLegend(false);
                bottomSilk.attrFilePolarity(true);

                pth.attrFileFunctionPlated(1, layerCount);
                pth.attrFilePolarity(true);

                allLayers.forEach(GerberWriter::fileAttributesFinished);
            } else {
                allLayers.forEach(GerberWriter::polarityDark);
            }

            double connectionWidth = 0.2;

            Via via = new Via(0.5, 0.3, connectionWidth, 0.2, 0.3);

            int x = 0;
            int y = 0;
            int i = 0;
            var pads = List.of(new Pad(0.3), new Pad(0.5));

            for (double outerGap : List.of(0.2, 0.3)) {
                for (double innerGap : List.of(0.15, 0.25)) {
                    double copperDiameter = 0.75;
                    CircularSolderJumper jumper = new CircularSolderJumper(copperDiameter, outerGap, innerGap,
                            connectionWidth);
                    drawBoard(x * raster, y * raster, raster, 4, 4,
                            jumper,
                            pads.get(i % pads.size()),
                            via);
                    i++;
                    x += 4;
                }
                y += 4;
                x = 0;
            }

        } finally {
            for (GerberWriter g : allLayers) {
                g.close();
            }
        }
    }

    void flushQueue() {
        queue.forEach(Runnable::run);
        queue.clear();
    }

    void queue(Runnable r) {
        queue.add(r);
    }

    private void drawBoard(double originX, double originY, double raster, int width, int height,
            SolderJumper jumper, Pad pad, Via via) {
        for (int ix = 0; ix < width; ix++) {
            for (int iy = 0; iy < height; iy++) {
                pad.flash(0, originX + ix * raster, originY + iy * raster, true);
                pad.flash(layerCount - 1, originX + ix * raster, originY + iy * raster, false);
            }
        }

        // copper pours
        for (int i = 1; i < layerCount - 1; i++) {
            var g = copperLayer(i);
            g.polarityDark().contour(() -> {
                g.move(originX, originY).linearInterpolation();
                g.interpolate(originX + width * raster, originY);
                g.interpolate(originX + width * raster, originY + height * raster);
                g.interpolate(originX, originY + height * raster);
            });

        }

        flushQueue();

        for (int ix = 0; ix < width; ix++) {
            for (int iy = 0; iy < height; iy++) {
                // top pad-to-pad jumpers
                if (ix > 0 && iy > 0) {
                    jumper.flash(0, originX + ix * raster, originY + iy * raster + raster / 2, JumperType.HORIZONTAL);
                    jumper.flash(0, originX + ix * raster + raster / 2, originY + iy * raster, JumperType.VERTICAL);

                    // bottom pad-to-pad jumpers
                    jumper.flash(layerCount - 1, originX + ix * raster, originY + iy * raster + raster / 2,
                            JumperType.HORIZONTAL);
                    jumper.flash(layerCount - 1, originX + ix * raster + raster / 2, originY + iy * raster,
                            JumperType.VERTICAL);
                }

                // bottom via jumper
                var padCenter = vector(originX + ix * raster + raster / 2, originY + iy * raster + raster / 2);
                jumper.flash(layerCount - 1, padCenter.x, padCenter.y, JumperType.CENTER);

                var viaLocation = vector(originX + ix * raster + raster * 0, originY + iy * raster + raster * 1);

                int viaLayer = 0;
                viaLayer = (ix % 2 == 1) && (iy % 2 == 0) ? 1 : viaLayer;
                viaLayer = (ix % 2 == 0) && (iy % 2 == 1) ? 2 : viaLayer;

                via.flash(viaLayer, viaLocation.x, viaLocation.y,
                        padCenter.minus(viaLocation).length() - jumper.connectionDistance() / 2,
                        -45);
            }
        }

        flushQueue();

    }

    private enum JumperType {
        VERTICAL,
        HORIZONTAL,
        CENTER,
    }

    private interface SolderJumper {
        void flash(int layer, double x, double y, JumperType type);

        double connectionDistance();
    }

    private class CircularSolderJumper implements SolderJumper {

        private double copperDiameter;
        private double outerGap;
        private double innerGap;
        private double connectionWidth;
        private double outerDiameter;

        public CircularSolderJumper(double copperDiameter, double outerGap, double innerGap,
                double connectionWidth) {
            this.copperDiameter = copperDiameter;
            this.outerGap = outerGap;
            this.innerGap = innerGap;
            this.connectionWidth = connectionWidth;
            this.outerDiameter = copperDiameter + 2 * outerGap;
        }

        @Override
        public double connectionDistance() {
            return outerDiameter;
        }

        @Override
        public void flash(int layer, double x, double y, JumperType type) {
            if (layer == layerCount - 1) {
                if (type == JumperType.CENTER) {
                    flash(layer, x - raster / 2 + outerDiameter - 0.05, y + raster / 2 - outerDiameter + 0.05, -45);
                    return;
                }
                flash(layer, x, y, -45);
                return;
            }

            switch (type) {
                case VERTICAL:
                    flash(layer, x, y, 90);
                    break;
                default:
                    flash(layer, x, y, 0);
            }
        }

        public void flash(int layer, double x, double y, double angle) {
            var outerDiameter = copperDiameter + 2 * outerGap;
            var g = copperLayer(layer);
            g
                    .polarityClear()
                    .apertureCircle(outerDiameter).flash(x, y);
            queue(() -> {
                maskLayer(layer).polarityClear().apertureCircle(outerDiameter).flash(x, y);
                maskLayer(layer).polarityDark().apertureCircle(copperDiameter + 2 * soldermaskExpansion).flash(x, y);

                g.polarityDark();
                g.apertureCircle(copperDiameter).flash(x, y);
                var v = vector(x, y);
                g.linearInterpolation();

                // connection
                g.apertureCircle(connectionWidth)
                        .move(vector(-outerDiameter / 2, 0).rotate(angle).add(v))
                        .interpolate(vector(outerDiameter / 2, 0).rotate(angle).add(v));

                // gap
                g.polarityClear().apertureCircle(innerGap)
                        .move(vector(0, -copperDiameter / 2).rotate(angle).add(v))
                        .interpolate(vector(0, copperDiameter / 2).rotate(angle).add(v))
                        .polarityDark();

                maskLayer(layer).polarityClear().apertureCircle(innerGap)
                        .move(vector(0, -copperDiameter / 2).rotate(angle).add(v))
                        .interpolate(vector(0, copperDiameter / 2).rotate(angle).add(v))
                        .polarityDark();
            });

        }
    }

    private class RectangularSolderJumper implements SolderJumper {

        private double width;
        private double length;
        private double outerGap;
        private double innerGap;
        private double connectionWidth;

        public RectangularSolderJumper(double width, double length, double outerGap, double innerGap,
                double connectionWidth) {
            this.width = width;
            this.length = length;
            this.outerGap = outerGap;
            this.innerGap = innerGap;
            this.connectionWidth = connectionWidth;
        }

        @Override
        public double connectionDistance() {
            return innerGap + width;
        }

        public void flash(int layer, double x, double y, JumperType type) {
            switch (type) {
                case VERTICAL:
                    flash(layer, x, y, 90);
                    break;
                case CENTER:
                    flash(layer, x, y, -45);
                    break;
                default:
                    flash(layer, x, y, 0);
            }
        }

        public void flash(int layer, double x, double y, double angle) {
            var v = vector(x, y);

            // opening in surrounding copper
            var g = copperLayer(layer);
            g
                    .polarityClear()
                    .rectangle(v, 2 * (width + outerGap) + innerGap, length + 2 * outerGap, angle);

            queue(() -> {
                // mask over outer gap
                maskLayer(layer).polarityClear().rectangle(v, 2 * (width + outerGap) + innerGap, length + 2 * outerGap,
                        angle);

                // no mask over jumper
                maskLayer(layer).polarityDark().rectangle(v, 2 * width + innerGap, length, angle);

                // jumper
                g.polarityDark().rectangle(v, 2 * width + innerGap, length, angle);

                g.linearInterpolation();

                // connection
                g.apertureCircle(connectionWidth)
                        .move(vector(-width - outerGap - innerGap / 2, 0).rotate(angle).add(v))
                        .interpolate(vector(width + outerGap + innerGap / 2, 0).rotate(angle).add(v));

                // gap
                g.polarityClear().apertureCircle(innerGap)
                        .move(vector(0, -length / 2).rotate(angle).add(v))
                        .interpolate(vector(0, length / 2).rotate(angle).add(v))
                        .polarityDark();

                maskLayer(layer).polarityClear().apertureCircle(innerGap)
                        .move(vector(0, -length / 2).rotate(angle).add(v))
                        .interpolate(vector(0, length / 2).rotate(angle).add(v))
                        .polarityDark();
            });

        }
    }

    private class Pad {

        private double gap;

        public Pad(double gap) {
            this.gap = gap;
        }

        public void flash(int layer, double x, double y, boolean mask) {
            copperLayer(layer).polarityDark().apertureRectangle(raster - gap, raster - gap).flash(x + raster / 2,
                    y + raster / 2);
            if (mask)
                maskLayer(layer).polarityDark()
                        .apertureRectangle(raster - gap + 2 * soldermaskExpansion,
                                raster - gap + 2 * soldermaskExpansion)
                        .flash(x + raster / 2,
                                y + raster / 2);
        }
    }

    private class Via {

        private double diameter;
        private double connectionWidth;
        private double gap;
        private double connectionGap;
        private double holeSize;

        public Via(double diameter, double holeSize, double connectionWidth, double gap, double connectionGap) {
            this.diameter = diameter;
            this.holeSize = holeSize;
            this.connectionWidth = connectionWidth;
            this.gap = gap;
            this.connectionGap = connectionGap;
        }

        public void flash(int layer, double x, double y, double connectionLength, double connectionAngle) {
            var v = vector(x, y);

            double outerDiameter = diameter + 2 * gap;

            // add gap around via on top, bottom and unconnected layers
            for (int i = 0; i < layerCount; i++) {
                if (i == 0 || i == layerCount - 1 || i != layer)
                    copperLayer(i).polarityClear().apertureCircle(outerDiameter).flash(x, y);
            }

            // gap for connection on bottom layer
            bottom
                    .polarityClear()
                    .apertureCircle(connectionWidth + 2 * connectionGap)
                    .linearInterpolation()
                    .move(v)
                    .interpolate(vector(connectionLength, 0).rotate(connectionAngle).add(v));

            bottomMask
                    .polarityClear()
                    .apertureCircle(connectionWidth + 2 * connectionGap)
                    .linearInterpolation()
                    .move(v)
                    .interpolate(vector(connectionLength, 0).rotate(connectionAngle).add(v));

            // circles on all layers
            queue(() -> {
                for (int i = 0; i < layerCount; i++) {
                    copperLayer(i).polarityDark().apertureCircle(diameter).flash(x, y);
                }
            });

            // connection on bottom layer
            queue(() -> {
                bottom.polarityDark().apertureCircle(connectionWidth)
                        .linearInterpolation()
                        .move(v)
                        .interpolate(vector(connectionLength, 0).rotate(connectionAngle).add(v));
            });

            // connection on top layer
            if (layer == 0)
                queue(() -> {
                    top.polarityDark().apertureCircle(connectionWidth)
                            .linearInterpolation()
                            .move(v)
                            .interpolate(vector(diameter / 2 + gap, 0).rotate(connectionAngle).add(v));
                });

            // make sure there is a mask on the outer layers
            topMask.polarityClear().apertureCircle(outerDiameter - 2 * soldermaskExpansion).flash(x, y);
            bottomMask.polarityClear().apertureCircle(outerDiameter - 2 * soldermaskExpansion).flash(x, y);

            // drill
            pth.polarityDark().apertureCircle(holeSize, new ApertureArgs("ViaDrill")).flash(x, y);

            // silk
            if (layer == 1) {
                topSilk.polarityDark().apertureCircle(diameter).flash(x, y);
                bottomSilk.polarityDark().apertureCircle(diameter).flash(x, y);
            }
            if (layer == 2) {
                topSilk
                        .polarityDark().apertureCircle(outerDiameter).flash(x, y)
                        .polarityClear().apertureCircle(diameter).flash(x, y);

                bottomSilk
                        .polarityDark().apertureCircle(outerDiameter).flash(x, y)
                        .polarityClear().apertureCircle(diameter).flash(x, y);
            }
        }
    }
}

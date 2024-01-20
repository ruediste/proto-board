package com.github.ruediste;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    public int layerCount = 4;
    List<GerberWriter> allLayers;

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

    public void run() throws Exception {
        UUID ident = UUID.randomUUID();
        var prefix = "proto-";
        top = new GerberWriter(ident, prefix + "F_Cu.gbr");
        topMask = new GerberWriter(ident, prefix + "F_Mask.gbr");
        topSilk = new GerberWriter(ident, prefix + "F_Silkscreen.gbr");
        in1 = new GerberWriter(ident, prefix + "In1_Cu.gbr");
        in2 = new GerberWriter(ident, prefix + "In2_Cu.gbr");
        bottom = new GerberWriter(ident, prefix + "B_Cu.gbr");
        bottomMask = new GerberWriter(ident, prefix + "B_Mask.gbr");
        bottomSilk = new GerberWriter(ident, prefix + "B_Silkscreen.gbr");
        pth = new GerberWriter(ident, prefix + "PTH-drl.gbr");

        allLayers = List.of(top, topMask, topSilk, in1, in2, bottom, bottomMask, bottomSilk, pth);

        try {
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

            double raster = 2.54;
            double connectionWidth = 0.15;
            Pad pad = new Pad(raster, 0.4);
            CircularSolderJumper jumper = new CircularSolderJumper(0.75, 0.2, 0.15, connectionWidth);
            Via via = new Via(raster * 0.25, 0.3, connectionWidth, 0.2, 0.3);

            drawBoard(0, 0, raster, 6, 6,
                    jumper,
                    pad,
                    via);

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
            CircularSolderJumper jumper, Pad pad, Via via) {
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
                    jumper.flash(0, originX + ix * raster, originY + iy * raster + raster / 2, 0);
                    jumper.flash(0, originX + ix * raster + raster / 2, originY + iy * raster, 90);

                    // bottom pad-to-pad jumpers
                    jumper.flash(layerCount - 1, originX + ix * raster, originY + iy * raster + raster / 2, -45);
                    jumper.flash(layerCount - 1, originX + ix * raster + raster / 2, originY + iy * raster, -45);
                }

                // bottom via jumper
                var padCenter = vector(originX + ix * raster + raster / 2, originY + iy * raster + raster / 2);
                jumper.flash(layerCount - 1, padCenter.x, padCenter.y, -45);

                var viaLocation = vector(originX + ix * raster + raster * 0, originY + iy * raster + raster * 1);

                int viaLayer = 0;
                viaLayer = (ix % 2 == 1) && (iy % 2 == 0) ? 1 : viaLayer;
                viaLayer = (ix % 2 == 0) && (iy % 2 == 1) ? 2 : viaLayer;

                via.flash(viaLayer, viaLocation.x, viaLocation.y,
                        padCenter.minus(viaLocation).length() - jumper.copperDiameter / 2 / 2,
                        -45);
            }
        }

        flushQueue();

    }

    private class CircularSolderJumper {

        private double copperDiameter;
        private double outerGap;
        private double innerGap;
        private double connectionWidth;

        public CircularSolderJumper(double copperDiameter, double outerGap, double innerGap,
                double connectionWidth) {
            this.copperDiameter = copperDiameter;
            this.outerGap = outerGap;
            this.innerGap = innerGap;
            this.connectionWidth = connectionWidth;

        }

        public void flash(int layer, double x, double y, double angle) {
            var outerDiameter = copperDiameter + 2 * outerGap;
            var g = copperLayer(layer);
            g
                    .polarityClear()
                    .apertureCircle(outerDiameter).flash(x, y);
            queue(() -> {
                maskLayer(layer).polarityClear().apertureCircle(copperDiameter + 2 * outerGap).flash(x, y);
                maskLayer(layer).polarityDark().apertureCircle(copperDiameter).flash(x, y);

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

    private class Pad {

        private double raster;
        private double gap;

        public Pad(double raster, double gap) {
            this.raster = raster;
            this.gap = gap;
        }

        public void flash(int layer, double x, double y, boolean mask) {
            copperLayer(layer).polarityDark().apertureRectangle(raster - gap, raster - gap).flash(x + raster / 2,
                    y + raster / 2);
            if (mask)
                maskLayer(layer).polarityDark().apertureRectangle(raster - gap, raster - gap).flash(x + raster / 2,
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

            // add gap around via on top, bottom and unconnected layers
            for (int i = 0; i < layerCount; i++) {
                if (i == 0 || i == layerCount - 1 || i != layer)
                    copperLayer(i).polarityClear().apertureCircle(diameter + 2 * gap).flash(x, y);
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
            topMask.polarityClear().apertureCircle(diameter + 2 * gap).flash(x, y);
            bottomMask.polarityClear().apertureCircle(diameter + 2 * gap).flash(x, y);

            pth.polarityDark().apertureCircle(holeSize, new ApertureArgs("ViaDrill")).flash(x, y);
        }
    }
}

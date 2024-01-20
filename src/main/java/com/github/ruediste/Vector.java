package com.github.ruediste;

public class Vector {
    public final double x;
    public final double y;

    public Vector(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Vector add(Vector other) {
        return new Vector(x + other.x, y + other.y);
    }

    public Vector minus(Vector other) {
        return new Vector(x - other.x, y - other.y);
    }

    public Vector scale(double factor) {
        return new Vector(x * factor, y * factor);
    }

    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    public Vector rotate(double angle) {
        var sin = Math.sin(angle * Math.PI / 180);
        var cos = Math.cos(angle * Math.PI / 180);
        return new Vector(x * cos - y * sin, x * sin + y * cos);
    }

    public static Vector vector(double x, double y) {
        return new Vector(x, y);
    }
}

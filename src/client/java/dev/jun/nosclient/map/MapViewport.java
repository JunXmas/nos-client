package dev.jun.nosclient.map;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class MapViewport {
    public double x;
    public double z;
    public double zoom;
    private double targetZoom;
    private double anchorX;
    private double anchorZ;
    private double pointerX;
    private double pointerY;
    private boolean anchored;
    private double targetX;
    private double targetZ;
    private boolean centering;

    public MapViewport(double x, double z, double zoom) {
        this.x = x;
        this.z = z;
        this.zoom = this.targetZoom = boundZoom(zoom);
    }

    private static double boundZoom(double zoom) {
        return Double.isFinite(zoom) ? Math.max(0.25, Math.min(8.0, zoom)) : 1.0;
    }

    public double worldX(double mouse, int width) {
        return this.x + (mouse - width / 2.0) / this.zoom;
    }

    public double worldZ(double mouse, int height) {
        return this.z + (mouse - height / 2.0) / this.zoom;
    }

    public void zoomAt(double mouseX, double mouseY, double wheel, int width, int height) {
        if (wheel != 0.0) {
            this.anchorX = this.worldX(mouseX, width);
            this.anchorZ = this.worldZ(mouseY, height);
            this.pointerX = mouseX;
            this.pointerY = mouseY;
            this.targetZoom = boundZoom(this.targetZoom * Math.pow(2.0, Math.max(-2.0, Math.min(2.0, wheel)) * 0.5));
            this.anchored = true;
            this.centering = false;
        }
    }

    public void center(double x, double z) {
        this.targetX = x;
        this.targetZ = z;
        this.centering = true;
        this.anchored = false;
    }

    public void pan(double dx, double dy) {
        this.x = this.x - dx / this.zoom;
        this.z = this.z - dy / this.zoom;
        this.anchored = false;
        this.centering = false;
    }

    public void step(double dt, int width, int height, boolean animated) {
        double k = animated ? 1.0 - Math.exp(-Math.max(0.0, Math.min(0.1, dt)) * 14.0) : 1.0;
        this.zoom = this.zoom + (this.targetZoom - this.zoom) * k;
        if (this.anchored) {
            this.x = this.anchorX - (this.pointerX - width / 2.0) / this.zoom;
            this.z = this.anchorZ - (this.pointerY - height / 2.0) / this.zoom;
        }

        if (this.centering) {
            this.x = this.x + (this.targetX - this.x) * k;
            this.z = this.z + (this.targetZ - this.z) * k;
        }

        this.x = Math.max(-2.9999984E7, Math.min(2.9999984E7, this.x));
        this.z = Math.max(-2.9999984E7, Math.min(2.9999984E7, this.z));
    }
}

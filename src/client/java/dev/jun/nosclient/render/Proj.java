package dev.jun.nosclient.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3fc;

public final class Proj {
    public static volatile float fovFromRenderer = Float.NaN;
    public static double cx;
    public static double cy;
    public static double cz;
    public static float yaw;
    public static float pitch;
    public static float width;
    public static float height;
    private static double fx;
    private static double fy;
    private static double fz;
    private static double rx;
    private static double ry;
    private static double rz;
    private static double ux;
    private static double uy;
    private static double uz;
    private static double focal;
    private static double focalBase = 1.0;

    private Proj() {}

    public static void update(float guiWidth, float guiHeight) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 p = cam.position();
        cx = p.x;
        cy = p.y;
        cz = p.z;
        yaw = cam.yRot();
        pitch = cam.xRot();
        width = guiWidth;
        height = guiHeight;
        Vector3fc f = cam.forwardVector();
        Vector3fc u = cam.upVector();
        Vector3fc l = cam.leftVector();
        fx = f.x();
        fy = f.y();
        fz = f.z();
        ux = u.x();
        uy = u.y();
        uz = u.z();
        rx = -l.x();
        ry = -l.y();
        rz = -l.z();
        float fov = fovFromRenderer;
        if (Float.isNaN(fov) || fov < 1.0F || fov > 179.0F) {
            fov = ((Integer) mc.options.fov().get()).intValue();
        }

        focal = guiHeight / 2.0 / Math.tan(Math.toRadians(fov) / 2.0);
        focalBase =
                guiHeight
                        / 2.0
                        / Math.tan(
                                Math.toRadians(((Integer) mc.options.fov().get()).intValue())
                                        / 2.0);
    }

    public static double focal() {
        return focal;
    }

    public static double zoomComp() {
        return focal <= 0.0 ? 1.0 : Math.pow(focalBase / focal, 0.85);
    }

    public static double[] dir(float yawDeg, float pitchDeg) {
        double yr = Math.toRadians(yawDeg);
        double pr = Math.toRadians(pitchDeg);
        return new double[] {
            -Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)
        };
    }

    public static boolean projectRel(double dx, double dy, double dz, double[] out) {
        double z = dx * fx + dy * fy + dz * fz;
        if (z < 0.05) {
            return false;
        }

        double x = dx * rx + dy * ry + dz * rz;
        double y = dx * ux + dy * uy + dz * uz;
        double k = focal / z;
        out[0] = width / 2.0 + x * k;
        out[1] = height / 2.0 - y * k;
        out[2] = z;
        out[3] = k;
        return true;
    }

    public static void unproject(float gx, float gy, float depth, float[] out) {
        double a = (gx - width / 2.0) / focal;
        double b = -(gy - height / 2.0) / focal;
        out[0] = (float) ((fx + a * rx + b * ux) * depth);
        out[1] = (float) ((fy + a * ry + b * uy) * depth);
        out[2] = (float) ((fz + a * rz + b * uz) * depth);
    }

    public static boolean project(double wx, double wy, double wz, double[] out) {
        return projectRel(wx - cx, wy - cy, wz - cz, out);
    }

    public static boolean onScreen(double[] p, double margin) {
        return p[0] > -margin && p[0] < width + margin && p[1] > -margin && p[1] < height + margin;
    }
}

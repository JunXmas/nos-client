package dev.jun.nosclient.media;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class SkyTextPlane {
    public static final float WORLD_SCALE = 0.9F;

    private SkyTextPlane() {
    }

    public static Quaternionf rotation(double bearing, double elevation) {
        return new Quaternionf().rotationYXZ((float)Math.toRadians(180.0 - bearing), (float)Math.toRadians(elevation), 0.0F);
    }

    public static Vector3f direction(double bearing, double elevation) {
        double yaw = Math.toRadians(bearing);
        double pitch = Math.toRadians(elevation);
        return new Vector3f((float)(-Math.sin(yaw) * Math.cos(pitch)), (float)Math.sin(pitch), (float)(Math.cos(yaw) * Math.cos(pitch)));
    }

    public static double density(int screenHeight, double fov, double size, double width, double height) {
        return screenHeight * 0.007499999801317851 * size * Math.max(width, height) / (2.0 * Math.tan(Math.toRadians(Math.clamp(fov, 5.0, 170.0) / 2.0)));
    }
}

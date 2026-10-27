package dev.jun.nosclient.feat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bản đồ + waypoint chơi đơn phải tách theo world: hai world cùng tên hiển thị "New World" không được ghi đè
 * nhau; world cũ quay lại vẫn còn dữ liệu; dữ liệu khoá cũ (theo tên) về đúng world có thư mục trùng tên.
 * Chạy: javac -d /tmp/wkc -cp <compileClasspath của client + build/classes/java/client> checks/WorldKeyCheck.java && java -ea -cp /tmp/wkc:<như trên> dev.jun.nosclient.feat.WorldKeyCheck
 */
public class WorldKeyCheck {
    public static void main(String[] args) throws Exception {
        Path saves = Files.createTempDirectory("nos-saves");
        String a = Waypoints.singleplayerKey(saves.resolve("New World"), 111L);
        String b = Waypoints.singleplayerKey(saves.resolve("New World (1)"), 222L);
        String again = Waypoints.singleplayerKey(saves.resolve("New World").resolve("."), 111L);
        assert !a.equals(b) : "hai world cùng tên hiển thị phải có khoá khác nhau";
        assert a.equals(again) : "quay lại world cũ phải ra đúng khoá cũ: " + a + " vs " + again;
        assert !a.equals(Waypoints.singleplayerKey(saves.resolve("New World"), 999L)) : "xoá world rồi tạo lại cùng tên (seed khác) phải mới tinh";
        assert Waypoints.dirName(a).matches("[A-Za-z0-9._-]+") : "tên thư mục bản đồ phải an toàn: " + Waypoints.dirName(a);

        // Dữ liệu khoá cũ "sp:New World" thuộc về world thư mục "New World", chỉ chuyển một lần.
        Path maps = Files.createTempDirectory("nos-map");
        Files.createDirectories(maps.resolve("sp_New_World").resolve("minecraft_overworld"));
        Files.writeString(maps.resolve("sp_New_World").resolve("minecraft_overworld").resolve("r.0.0.png"), "x");
        Map<String, List<Waypoints.Wp>> wps = new HashMap<>();
        wps.put("sp:New World", new ArrayList<>(List.of(new Waypoints.Wp("nhà", 1, 2, 3, "minecraft:overworld", 0, false))));
        assert Waypoints.migrate(wps, maps, "sp:New World", a);
        assert wps.get(a).size() == 1 && !wps.containsKey("sp:New World") : wps;
        assert Files.exists(maps.resolve(Waypoints.dirName(a)).resolve("minecraft_overworld").resolve("r.0.0.png")) : "bản đồ cũ phải theo về";
        // World thứ hai cùng tên hiển thị nhưng thư mục khác: không nhận gì, mới tinh.
        assert !Waypoints.migrate(wps, maps, "sp:New World (1)", b);
        assert !wps.containsKey(b) && !Files.exists(maps.resolve(Waypoints.dirName(b))) : "world mới phải trống";
        // Chạy lại không chép đôi.
        assert !Waypoints.migrate(wps, maps, "sp:New World", a) && wps.get(a).size() == 1;
        System.out.println("WorldKey OK");
    }
}

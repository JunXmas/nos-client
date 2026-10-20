# Nos client

Mod client Fabric 1.21.11 của Nostalgia Launcher.

Bản 0.1: màn chính là cảnh hồ 3D (anh đào, mía, cừu, hoàng hôn → đêm) có shader riêng —
bóng đổ, AO, mây khối, nước gương, tia nắng — thay panorama gốc. Bảng **Cảnh nền** ở góc phải
màn chính bật/tắt anh đào, mía, shader và kéo hoàng hôn ↔ đêm; lưu ở `config/nosclient.properties`.

HUD trong game (Fabric HudElementRegistry, vẽ sau tâm ngắm): FPS + 1% low, toạ độ + biome, la bàn dải,
ngày (đọc mỗi 2 giây), CPS (đệm vòng 1 giây), ping, keystrokes. Mặc định: toạ độ, hướng, FPS, keystrokes bật;
ngày, ping, CPS tắt — đổi trong `config/nosclient.properties` (`hud.*`). Tự kiểm CPS:
`java -cp build/classes/java/client dev.jun.nosclient.hud.Cps`.

Cảnh được port từ bản preview HTML đã duyệt (`~/nostalgia-hud-preview/preview.html`).
Texture đọc từ chính game (theo resource pack đang bật), mod không chép texture của Mojang.

Giao diện theo preview, font Inter/Manrope đóng gói trong mod (SIL OFL 1.1, giấy phép trong `META-INF/licenses/`):
- **Màn chính** thay màn vanilla: logo Nostalgia, menu kính mica có icon và dòng phụ (Enter = Chơi đơn), chip tài khoản,
  bảng Cảnh nền, chân trang. Các nút mở đúng màn của vanilla.
- **Cài đặt Nos client** (R-Shift ở màn chính và trong game): danh mục bên trái, lưới thẻ mod có công tắc trượt.
  Mới có nhóm HUD; các danh mục khác hiện "—" cho tới khi làm.

## Build

    ./gradlew build        # ra build/libs/nosclient-<version>.jar

Cần JDK 21. Thả jar cùng Fabric API vào thư mục `mods/`.

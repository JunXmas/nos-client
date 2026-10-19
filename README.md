# Nos client

Mod client Fabric 1.21.11 của Nostalgia Launcher.

Bản 0.1: màn chính là cảnh hồ 3D (anh đào, mía, cừu, hoàng hôn → đêm) có shader riêng —
bóng đổ, AO, mây khối, nước gương, tia nắng — thay panorama gốc. Bảng **Cảnh nền** ở góc phải
màn chính bật/tắt anh đào, mía, shader và kéo hoàng hôn ↔ đêm; lưu ở `config/nosclient.properties`.

Cảnh được port từ bản preview HTML đã duyệt (`~/nostalgia-hud-preview/preview.html`).
Texture đọc từ chính game (theo resource pack đang bật), mod không chép texture của Mojang.

## Build

    ./gradlew build        # ra build/libs/nosclient-<version>.jar

Cần JDK 21. Thả jar cùng Fabric API vào thư mục `mods/`.

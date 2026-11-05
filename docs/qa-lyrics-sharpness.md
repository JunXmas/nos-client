# QA chữ lyrics và tiếng Việt

Bản sửa trên nền 0.4.2, kiểm trước khi phát hành 0.4.3. Minecraft 1.21.11, Fabric Loader 0.19.5, Java 21,
Linux; dữ liệu game, world, cấu hình, D-Bus và player giả đều được cô lập.

## Thay đổi

- Karaoke đặt lại bề rộng tô khi đổi câu, giới hạn hình tô trong vùng lyrics và giới hạn
  pill theo chiều rộng cửa sổ. Không thay cách chọn kiểu lyrics đã lưu.
- Float/Sky LF dùng atlas Nos Celestial theo mật độ pixel trên màn hình thay vì atlas
  cố định thấp. Các font khác dùng profile lyrics oversample 8, profile sky oversample 16.
- Thêm lựa chọn Be Vietnam Pro từ font semibold/OFL đã đóng gói; giữ fallback Unicode.
  Chuẩn hóa NFC trước khi tạo component chữ, hỗ trợ dấu tổ hợp tiếng Việt.

## Kết quả đã quan sát

| Kiểm tra | Kết quả |
| --- | --- |
| Gradle offline build/test/remap verifier | Đạt; 75 JUnit, không lỗi hoặc bỏ qua |
| Coverage font | Đủ chữ U+1EA0–U+1EF9, các chữ Ă/Â/Đ/Ê/Ô/Ơ/Ư và dấu tổ hợp |
| Dịch | 788 khóa tiếng Anh/Việt, tham số định dạng hợp lệ |
| Mixin | Các target được đối chiếu jar Minecraft named 1.21.11, đạt |
| Shader core | Mọi biến thể trong checker biên dịch qua EGL NVIDIA và Mesa |
| Game FO, bỏ Iris | Hoàn tất 24 font, 8 trang hub, input/HUD/mây/mưa và các cảnh lyrics |
| Ảnh framebuffer | Đã xem Float/Sky, dấu tiếng Việt và karaoke 960×540 scale 1,8; không thấy phần tô tràn vùng lyrics |
| Iris + Complementary Reimagined r5.9.3 | Không hoàn tất: JVM dừng với mã 143, không có marker complete; chưa xác định nguyên nhân |

Lượt FO dùng llvmpipe; FPS trong ảnh không đại diện hiệu năng GPU của người chơi. Không
suy ra tương thích mọi shaderpack hoặc mọi cấu hình từ các kết quả này. Lượt kiểm nhanh
bỏ 58 cảnh preset bầu trời; phần sửa không thay shader hoặc preset.

Artifact QA `nosclient-0.4.2.jar` có SHA-256:
`33dda27e77531dc48b0db1cb4849e7f1036d9a4e8045a8b6e7af9d7d8acf219e`.
Đây là jar của nhánh sửa, khác bản 0.4.2 đã phát hành; không dùng làm gói cập nhật công khai.

## File sửa

- `src/client/java/dev/jun/nosclient/Ui.java`: dùng chung fallback với renderer lyrics.
- `src/client/java/dev/jun/nosclient/render/{Fonts,Gfx,WorldLyrics,TopBar}.java`.
- `src/main/resources/assets/nosclient/font/be-vietnam{,_lyrics,_sky}.json`.
- 21 profile `*_sky.json` có sẵn: chỉ tăng oversample.
- `src/test/java/dev/jun/nosclient/LyricFontCoverageTest.java`.
- `checks/game/fixtures.py`, `checks/game/java/dev/jun/nosclient/checks/GameVerification.java`.
- `README.md`, `CHANGELOG.md`, `checks/README.md` và tài liệu QA này.

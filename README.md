# Nos Client

Mod phía client của Nostalgia Launcher, dành cho Minecraft 1.21.11, Fabric Loader 0.19.3 trở lên,
Fabric API và Java 21.

## Bản 0.4.0

- Một hub cài đặt chung cho HUD, bản đồ, lời nhạc, trình phát, bầu trời và phụ kiện.
- Đọc nhạc từ MPRIS trên Linux, SMTC trên Windows, Music/Spotify trên macOS và Spotify Web.
  Có điều khiển phát, tua và ảnh bìa. Điều chỉnh âm lượng ứng dụng hỗ trợ trên Windows.
- Lời trên trời dùng chuyển động và bố cục Nos, nhận thời gian từng chữ từ các nguồn lời đã tích hợp.
  Có kiểu trôi nổi, font tùy chọn, độ lệch toàn cục/từng bài và nhập file LRC.
- Giữ 29 preset bầu trời, ảnh nền riêng, màu, thời tiết, sao, cực quang, tia sáng và phản ứng theo nhạc.
  Aurora Nos là lớp chồng tùy chọn. Màu sương lấy từ preset đang chọn.
- Karaoke dùng trình chỉnh vị trí HUD của Nos. Phụ kiện chỉ hiện trên nhân vật của chính bạn.
- Cấu hình chung ở `config/nosclient.json`, chia thành nhóm `client`, `music`, `effects`, `hud`.
  Lần chạy đầu chuyển cài đặt và tài nguyên cũ; giữ nguyên file cũ để có thể quay lại.

## Sử dụng

Nhấn **R-Shift** để mở hub, **U** để mở thẻ lời nhạc, **;** để mở trình phát.
Các phím đều đổi được trong cài đặt. Phím nhạc không tác động khi đang nhập trong menu hoặc chat.
Bật thẻ **Lời nhạc** rồi phát nhạc bằng ứng dụng hoặc trình duyệt.

Kéo file `.lrc` vào hub khi đang có bài phát để nhập lời cho bài đó. Mục mở thư mục lời cho biết tên file
tương ứng với bài hiện tại. LRC nhập tay được ưu tiên hơn lời tải về. Cache JSON giữ mốc từng chữ.

Bầu trời mặc định dùng preset Aurora. Mod có thể ẩn mây và thay mưa ở phía client theo cài đặt bầu trời;
khôi phục khi tắt hoặc rời thế giới. Tự điều chỉnh hiệu ứng theo FPS không sửa các tùy chọn đồ họa vanilla.

## Build và kiểm thử

```sh
./gradlew build test
python3 checks/localization.py
```

Jar tại `build/libs/nosclient-0.4.0.jar`. Thả jar cùng Fabric API vào thư mục `mods/`.
Bộ kiểm trong `checks/` gồm kiểm descriptor mixin, biên dịch shader, môi trường MPRIS và chạy Minecraft
thật với world/cấu hình riêng. Mod kiểm thử được đóng riêng bằng `./gradlew remapVerificationJar`;
nó không nằm trong jar phát hành. Xem [hướng dẫn kiểm thử](checks/README.md) và
[báo cáo QA 0.4.0](docs/qa-0.4.0.md) để biết phạm vi kiểm chứng, ảnh và số đo.

## Ghi công và giấy phép

Xem [CREDITS.md](CREDITS.md) cho LyricsFlow của 374m và các font đi kèm.
Mã nguồn công khai để xem; điều kiện sử dụng và phân phối nằm trong [LICENSE](LICENSE).
Các font giữ giấy phép riêng trong tài nguyên của mod.

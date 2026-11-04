# Kiểm thử Nos Client

## Kiểm tra JVM

```sh
./gradlew build test remapVerificationJar writeVerificationClasspath
python3 checks/localization.py
```

`build/libs/nosclient-0.4.1.jar` là mod phát hành. Jar trong `build/verification/` là mod điều khiển
kiểm thử riêng, không được cài vào game sử dụng hằng ngày. CI chạy build/test và biên dịch mod kiểm thử
trên Linux lẫn Windows bằng Java 21.

## Chạy Minecraft thật

Harness Linux cần Xvfb, ImageMagick, Python có Gio/GLib, `dbus-run-session`, `gdbus` và bản Fabric
1.21.11 đã cài trong Nostalgia. Đặt `NOSTALGIA` nếu dữ liệu launcher nằm ngoài vị trí mặc định.

Dùng **bản sao** của save làm world kiểm thử, đặt tên thư mục `Probe` và bật lệnh ở bản sao. Harness tiếp
tục chép world đó vào thư mục tạm. Không dùng world đang mở. Các lệnh dùng world chỉ để dựng camera,
chế độ sáng tạo, thời gian và thời tiết trong bản sao.

```sh
bash checks/run-game-verification.sh /tmp/nos-visual --world /path/to/Probe --fo
```

Mỗi lượt cần thư mục kết quả mới. Harness dùng game directory, cấu hình, Java user home và các thư mục
XDG riêng; không đổi `HOME`. Nguồn nhạc giả chạy trên D-Bus riêng nên không điều khiển ứng dụng thật.
Fixture cố ý có cấu hình cosmetic LF cũ để kiểm chúng bị bỏ qua mà không ảnh hưởng nhạc/trời.
Với `--fo`, mod/cấu hình FO được chép từ instance `optimized-1.21.11`.

Mặc định kiểm 29 preset ngày/đêm, 23 font, 8 trang hub, trình phát, lời trôi nổi, nhân vật vanilla, cửa sổ nhỏ/lớn,
kéo ô karaoke, scale/opacity, bảo toàn mây/mưa và phím khi mở menu. Kiểm ba lựa chọn lyrics bằng
click thật; các cảnh LF câu ghép cặp, LF rải từ, Nos riêng, khóa theo góc nhìn và chữ gọn xác nhận
renderer đang chọn hoạt động và không vẽ chồng. Kiểm các điều khiển chính không trùng giữa các trang. Các trang hub được bấm/kéo thật;
verifier từ chối màn hình khác và phát hiện phím media bị trùng.

Các biến tùy chọn:

| Biến | Tác dụng |
| --- | --- |
| `NOS_DISABLE_IRIS=1` | Bỏ Iris khỏi bản sao FO |
| `NOS_VERIFY_QUICK=true` | Bỏ vòng chụp 58 ảnh preset |
| `NOS_JVM=-Dnos.verify.uiOnly=true` | Bỏ vòng 23 font |
| `NOS_SHADERPACK=/path/pack.zip` | Chép và bật shaderpack trong bản sao |
| `NOS_SHADER_OPTIONS` | Nội dung tùy chọn shaderpack, mỗi dòng `key=value` |
| `NOS_JAR=/path/mod.jar` | Chọn jar cần kiểm |
| `DISPLAY_NO=96` | Chọn display Xvfb riêng |

`complete.txt` xác nhận các assertion đã qua. Thiếu marker hoặc có `failed.txt` là thất bại. Kết quả
còn có ảnh framebuffer, log, SHA-256 jar và cấu hình/options trước/sau. Nếu JVM dừng bất ngờ, kiểm thêm
mã thoát, `crash-reports`, `hs_err_pid*.log` và nhật ký OOM của máy.

## So FPS

```sh
NOS_VERIFY_MODE=performance NOS_VERIFY_SCENE=off \
  bash checks/run-game-verification.sh /tmp/nos-perf-off --world /path/to/Probe
```

Các cảnh: `off` tắt nhạc/trời; `idle` chỉ đọc nguồn nhạc; `aurora` lời và Aurora Nos; `sky` chỉ bầu trời
Aurora LF; `default` bầu trời LF cùng lời. Mỗi cảnh chờ 30 giây rồi lấy 30 mẫu FPS, giới hạn 260,
giữ camera/tầm nhìn/thời gian giống nhau, chống giảm FPS vì AFK. Chạy tuần tự, không build hoặc mở game
khác trong lúc đo. Khi đối chứng 0.3.1, thêm `NOS_JAR` và `NOS_VERIFY_BASELINE=1`; bản cũ đọc LRC thay
cho cache mốc từng chữ.

## Điều khiển nhạc và môi trường launcher

```sh
bash checks/run-media-verification.sh /tmp/nos-media
```

Kiểm phát/tạm dừng, tua tới/lùi, chuyển bài và vị trí khi tạm dừng trên player giả. Có thể thêm
`NOS_VERIFY_LAUNCHER_LIB=/path/Nostalgia/_internal` để xác nhận thư viện đóng gói không làm hỏng gdbus
hệ thống. Không dùng D-Bus của ứng dụng thật cho phép kiểm điều khiển này.

## Shader và mixin

```sh
python3 -I checks/glsl_check.py src/main/resources/assets/nosclient/shaders/core /path/minecraft-shader-includes
python3 checks/mixin_targets.py src/client/java /path/named-client.jar /path/named-common.jar
```

Shader checker thử các biến thể trên từng driver EGL đã cài. Cần quyền truy cập driver đồ họa; lỗi
khởi tạo EGL phải được phân biệt với lỗi biên dịch shader. Các jar của mixin checker phải dùng tên Mojang.

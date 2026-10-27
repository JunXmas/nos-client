#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;
// Lòng chữ poster "trong suốt": lấy cảnh phía sau từ bản chụp khung hình (Sampler1, toạ độ màn hình), đảo độ sáng,
// tăng tương phản rồi ánh xạ lên dải màu đo từ ảnh mẫu của jun: tím đậm ← trời/mây sáng, hồng rực ← cây/vật tối,
// đỏ-hồng ở dải giữa. Vân lá, mây, núi vẫn hiện rõ trong chữ, chữ đổi màu theo cảnh khi quay đầu.
// Dải màu dựng từ mực (màu người chơi chọn), theo đúng cấu trúc đo từ ảnh mẫu của jun:
// tối sâu → mực đậm → mực → mực sáng → gần trắng ánh mực. Mặc định hồng F826BE ra đúng dải tím→magenta của ảnh mẫu;
// chọn cam thì ra nâu→cam→vàng nhạt, chọn xanh thì ra navy→xanh→trắng xanh.
vec3 ramp(float t, vec3 ink) {
    vec3 c0 = ink * 0.38;
    vec3 c1 = ink * 0.72;
    vec3 c2 = ink;
    vec3 c3 = mix(ink, vec3(1.0), 0.28);
    vec3 c4 = mix(ink, vec3(1.0), 0.6);
    return t < 0.25 ? mix(c0, c1, t * 4.0)
         : t < 0.5 ? mix(c1, c2, (t - 0.25) * 4.0)
         : t < 0.75 ? mix(c2, c3, (t - 0.5) * 4.0)
         : mix(c3, c4, (t - 0.75) * 4.0);
}
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
void main() {
    float c = texture(Sampler0, texCoord0).r * vertexColor.a * ColorModulator.a;
    if (c <= 0.004) discard;
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec2 px = 1.0 / ScreenSize;
    vec3 scene = texture(Sampler1, uv).rgb;
    float here = luma(scene);
    // Độ sáng trung bình quanh điểm (ô ~9 px): trừ đi để lấy CHI TIẾT cảnh (cạnh lá, mây, khối). Trên nền trơn chi
    // tiết ≈ 0 nên chữ vẫn có vân từ độ sáng chung; trên lá/cây chữ lấm tấm như ảnh mẫu.
    float around = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            around += luma(texture(Sampler1, uv + vec2(i, j) * px * 4.0).rgb);
        }
    }
    around /= 9.0;
    float detail = (here - around) * 3.2;
    // Ánh xạ độ sáng → vị trí trên dải: trời/mây sáng (≥ 0.65) về đầu tím, cây/vật tối (≤ 0.3) về magenta.
    // Nền tối chỉ tới magenta rực (0.78); hồng nhạt ở đỉnh dải chỉ dành cho chi tiết sáng — ảnh mẫu chỉ ~10% điểm
    // ở tông đó, đêm mà kéo cả chữ lên thì nhợt.
    float t = clamp(min((0.72 - here) / 0.5, 0.78) - detail, 0.0, 1.0);
    vec3 tint = mix(vec3(1.0), scene / max(here, 0.05), 0.12);
    vec3 ink = clamp(vertexColor.rgb * ColorModulator.rgb, 0.0, 1.0);
    fragColor = vec4(clamp(ramp(t, ink) * tint, 0.0, 1.0), c);
}

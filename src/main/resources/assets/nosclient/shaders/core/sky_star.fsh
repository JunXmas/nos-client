#version 330
#moj_import <minecraft:dynamictransforms.glsl>
in vec2 local;
in vec4 vertexColor;
out vec4 fragColor;
// Hạt sáng cộng dồn. local.x > 1.5 là vệt sao băng (chỉ local.y chạy -1..1 ngang vệt, độ mờ dọc vệt đi theo màu đỉnh);
// còn lại là ngôi sao: lõi + quầng + tia chữ thập, local trong [-1, 1], tắt hẳn ở mép quad.
void main() {
    float k;
    if (local.x > 1.5) {
        float v = local.y;
        k = exp(-v * v * 5.0) * (1.0 - v * v);
    } else {
        float r2 = dot(local, local);
        float core = exp(-r2 * 22.0);
        float halo = exp(-r2 * 4.0) * 0.4;
        float cross = (exp(-abs(local.x) * 28.0) + exp(-abs(local.y) * 28.0)) * exp(-r2 * 2.5) * 0.45;
        k = (core + halo + cross) * clamp(1.0 - r2, 0.0, 1.0);
    }
    k *= vertexColor.a * ColorModulator.a;
    if (k <= 0.002) discard;
    fragColor = vec4(vertexColor.rgb * ColorModulator.rgb * k, k);
}

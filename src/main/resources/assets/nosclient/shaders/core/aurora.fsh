#version 330
#moj_import <minecraft:dynamictransforms.glsl>
in vec2 local;
in vec4 vertexColor;
out vec4 fragColor;
// Màn cực quang. local.x chạy dọc dải (đã cộng pha trôi ở CPU), local.y 0 = chân màn, 1 = đỉnh.
// Ba tầng màu như cực quang thật: chân viền xanh lục sáng (oxy 557 nm), thân lục ngả lam, đỉnh ngả màu nhịp
// (vertexColor.rgb — đổi theo câu hát). Tia dọc mảnh từ ba lớp nhiễu, viền chân lượn sóng nhỏ, đỉnh tan mềm.
// vertexColor.a = độ sáng tổng (đêm/ngày, nhịp). Cộng dồn nên không cần sắp xếp.
float hash(float n) { return fract(sin(n * 12.9898) * 43758.5453); }
float noise(float x) {
    float i = floor(x);
    float f = fract(x);
    return mix(hash(i), hash(i + 1.0), f * f * (3.0 - 2.0 * f));
}
void main() {
    float x = local.x;
    // Chân màn không thẳng: lượn nhẹ theo x để viền sáng gợn như rèm.
    float hemY = 0.05 * noise(x * 3.0 + 11.0);
    float y = clamp(local.y - hemY, 0.0, 1.0);
    float rays = 0.40 * noise(x * 5.0) + 0.30 * noise(x * 13.0 + 3.1) + 0.30 * pow(noise(x * 37.0 + 7.7), 3.0) * 2.0;
    float body = smoothstep(0.0, 0.05, y) * pow(1.0 - y, 2.2);
    float hem = exp(-y * 18.0) * 0.9;
    float k = (body * (0.25 + 0.85 * rays) + hem * (0.5 + 0.5 * rays)) * vertexColor.a * ColorModulator.a;
    if (k <= 0.002) discard;
    vec3 hemColor = vec3(0.55, 1.00, 0.70);
    vec3 green = vec3(0.18, 0.95, 0.55);
    vec3 teal = vec3(0.10, 0.70, 0.85);
    vec3 col = mix(hemColor, green, smoothstep(0.0, 0.12, y));
    col = mix(col, teal, smoothstep(0.25, 0.5, y) * 0.5);
    col = mix(col, vertexColor.rgb * 1.2, smoothstep(0.35, 0.8, y));
    fragColor = vec4(col * k, k);
}

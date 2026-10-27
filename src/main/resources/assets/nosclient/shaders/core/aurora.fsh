#version 330
#moj_import <minecraft:dynamictransforms.glsl>
in vec2 local;
in vec4 vertexColor;
out vec4 fragColor;
// Màn cực quang. local.x chạy dọc dải (đã cộng pha thời gian ở CPU nên các tia trôi), local.y 0 = chân màn, 1 = đỉnh.
// Chân màn có viền sáng xanh lục, thân màn là các tia dọc nhạt dần lên trên và ngả sang màu đỉnh (vertexColor.rgb,
// lấy từ màu chữ lyrics). vertexColor.a = độ sáng (đã gồm nhịp lời nhạc, đêm/ngày). Cộng dồn nên không cần sắp xếp.
float hash(float n) { return fract(sin(n * 12.9898) * 43758.5453); }
float noise(float x) {
    float i = floor(x);
    float f = fract(x);
    return mix(hash(i), hash(i + 1.0), f * f * (3.0 - 2.0 * f));
}
void main() {
    float y = clamp(local.y, 0.0, 1.0);
    float rays = 0.45 * noise(local.x * 6.0) + 0.35 * noise(local.x * 17.0 + 3.1) + 0.2 * noise(local.x * 41.0 + 7.7);
    float body = smoothstep(0.0, 0.06, y) * pow(1.0 - y, 1.8);
    float hem = exp(-y * 14.0) * 0.6;
    float k = (body * (0.3 + 0.9 * rays) + hem * rays) * vertexColor.a * ColorModulator.a;
    if (k <= 0.002) discard;
    vec3 green = vec3(0.30, 1.00, 0.62);
    vec3 col = mix(green, vertexColor.rgb * 1.25, smoothstep(0.05, 0.5, y));
    fragColor = vec4(col * k, k);
}

#version 330
#moj_import <minecraft:dynamictransforms.glsl>
in vec2 localPos;
flat in vec2 halfSize;
in vec4 vertexColor;
out vec4 fragColor;
// Khoảng loang của bóng/quầng: quad vẽ to hơn khối thật đúng bấy nhiêu mỗi phía (khớp Glass.SPREAD).
const float SPREAD = 10.0;
float box(vec2 p, vec2 extent, float radius) {
    vec2 q = abs(p) - extent + radius;
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
}
void main() {
#ifdef SHADOW
    vec2 extent = max(halfSize - SPREAD, vec2(0.5));
#else
    vec2 extent = halfSize;
#endif
    float radius = min(5.0, min(extent.x, extent.y));
    float d = box(localPos, extent, radius);
    float aa = max(fwidth(d), 0.0001);
    float coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, d);
#ifdef OUTLINE
    coverage *= smoothstep(-aa * 0.5, aa * 0.5, d + 0.75);
#endif
#ifdef SHADOW
    // Bóng/quầng mềm tắt dần ra ngoài mép, khoét rỗng phần dưới khối để không làm đục kính.
    float k = 1.0 - smoothstep(-1.0, SPREAD, d);
    coverage = k * k * smoothstep(-2.0, 0.5, d);
#endif
#ifdef SHEEN
    // Ánh kính: dải sáng phủ nửa trên nhạt dần, cộng một đường viền sáng mảnh ở mép trên.
    float t = (localPos.y + extent.y) / max(2.0 * extent.y, 1.0);
    float rim = (1.0 - smoothstep(0.0, 1.4, -d)) * (1.0 - smoothstep(0.0, 0.35, t));
    coverage *= (1.0 - smoothstep(0.0, 0.55, t)) * 0.55 + rim;
#endif
    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.001) discard;
    fragColor = color;
}

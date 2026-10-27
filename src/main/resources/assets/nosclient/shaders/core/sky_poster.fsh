#version 330
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 worldPos;
out vec4 fragColor;
// Lòng chữ "poster": đảo màu cảnh phía sau rồi ánh xạ sang hai tông mực, ba lượt blend trên cùng hình chữ
// (blend đặt ở SkyTextMaterial, mỗi lượt một define):
//   INVERT  d ← c·(1 − d) + d·(1 − c)           (đảo cảnh, c = độ phủ nét)
//   SCALE   d ← d · (1 − c·(1 − K)),  K = A − B
//   ADD     d ← d + c·B
// Ở c = 1: kết quả = B + (1 − cảnh)·(A − B) → cảnh sáng ra B (tím đậm), cảnh tối ra A (mực, hồng rực), vân
// cảnh vẫn hiện trong lòng chữ và mỗi kênh luôn nằm giữa B và A nên màu không bao giờ lệch khỏi tông mực.
void main() {
    float c = texture(Sampler0, texCoord0).r * vertexColor.a * ColorModulator.a;
    if (c <= 0.004) discard;
    vec3 inkA = clamp(vertexColor.rgb * ColorModulator.rgb, 0.0, 1.0);
    vec3 inkB = inkA * vec3(0.35, 0.26, 0.60);
#if defined(PASS_INVERT)
    fragColor = vec4(vec3(c), c);
#elif defined(PASS_SCALE)
    vec3 k = inkA - inkB;
    fragColor = vec4(vec3(1.0) - c * (vec3(1.0) - k), 1.0);
#else
    fragColor = vec4(inkB * c, c);
#endif
}

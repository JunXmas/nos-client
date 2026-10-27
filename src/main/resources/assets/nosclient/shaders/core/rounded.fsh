#version 330
#moj_import <minecraft:dynamictransforms.glsl>
in vec2 localPos;
flat in vec2 halfSize;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    float radius = min(5.0, min(halfSize.x, halfSize.y));
    vec2 q = abs(localPos) - halfSize + radius;
    float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float aa = max(fwidth(d), 0.0001);
    float coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, d);
#ifdef OUTLINE
    coverage *= smoothstep(-aa * 0.5, aa * 0.5, d + 0.75);
#endif
    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.001) discard;
    fragColor = color;
}

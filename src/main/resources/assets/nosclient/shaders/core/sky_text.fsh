#version 330
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    float coverage = texture(Sampler0, texCoord0).r;
    float alpha = coverage * vertexColor.a * ColorModulator.a;
    if (alpha <= 0.001) discard;
    fragColor = vec4(vertexColor.rgb * ColorModulator.rgb, alpha);
}

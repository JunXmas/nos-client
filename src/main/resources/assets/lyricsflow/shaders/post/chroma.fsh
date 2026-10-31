#version 330

// LyricsFlow Chroma: the blocks around you keep their colors, everything further away turns grey (tinted).
// The circle is measured per block on the ground around the player, so its edge follows the blocks.

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;

in vec2 texCoord;

layout(std140) uniform ChromaConfig {
    mat4 InvViewProj;   // screen -> world, relative to the camera
    vec4 CamFract;      // xyz: camera position minus its block position
    vec4 PlayerRel;     // xyz: player position minus the camera block, w: radius in blocks
    vec4 Params;        // x: edge softness (blocks), y: gamma, z: strength 0..1
    vec4 Tint;          // rgb
    vec4 Lift;          // rgb
};

out vec4 fragColor;

// stable hash without sin(): sin of big numbers is imprecise on many GPUs and made the edge shimmer
float hash3(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

// smooth value noise: lumps a few texture pixels big
float vnoise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(hash3(i), hash3(i + vec3(1, 0, 0)), f.x), mix(hash3(i + vec3(0, 1, 0)), hash3(i + vec3(1, 1, 0)), f.x), f.y);
    float b = mix(mix(hash3(i + vec3(0, 0, 1)), hash3(i + vec3(1, 0, 1)), f.x), mix(hash3(i + vec3(0, 1, 1)), hash3(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(a, b, f.z);
}

void main() {
    vec4 color = texture(InSampler, texCoord);
    float depth = texture(InDepthSampler, texCoord).r;
    // the sky (and the LyricsFlow sky effects on it) keeps its colors
    float k = 0.0;
    if (depth < 1.0) {
        vec4 ndc = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec4 rel = InvViewProj * ndc;
        rel.xyz /= rel.w;
        // the texture pixel (1/16 block) this pixel belongs to: the edge is cut pixel by pixel
        // step a hair into the surface along the view ray: a pixel right on a block face used to fall on one side
        // or the other as you turned the camera, so its 1/16 cell (and the edge there) blinked
        vec3 local = rel.xyz + normalize(rel.xyz) * 0.02 + CamFract.xyz;
        vec3 px = floor(local * 16.0);
        vec3 pc = (px + 0.5) / 16.0;
        float d = length(pc.xz - PlayerRel.xz);
        // ragged, eaten-away edge like the reference: lumps plus single pixel crumbs, fixed to the world
        vec3 world = mod(px + vec3(Params.w, Tint.w, Lift.w) * 16.0, 1024.0);
        float lumps = vnoise(world / 5.0) - 0.5;
        // crumbs two texture pixels big: single 1/16 cells far away are smaller than a screen pixel and sparkled
        float crumbs = hash3(floor(world / 2.0)) - 0.5;
        float edge = PlayerRel.w + lumps * 1.6 + crumbs * 0.45;
        k = step(edge, d);
    }
    k *= Params.z;
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    float l = pow(clamp(luma, 0.0, 1.0), Params.y);
    vec3 grey = clamp(Lift.rgb + vec3(l) * Tint.rgb, 0.0, 1.0);
    fragColor = vec4(mix(color.rgb, grey, k), color.a);
}

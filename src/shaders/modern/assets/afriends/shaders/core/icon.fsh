#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <afriends:common.glsl>

const float GLOW_SCALE = 256.0;

uniform sampler2D Sampler0;

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord;

layout(location = 2) flat in ivec2 params;

layout(location = 0) out vec4 fragColor;

void main() {
    float field = texture(Sampler0, texCoord).r;
    float spread = float(params.x);
    float glow = float(params.y) / GLOW_SCALE;

    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    float texelsPerPixel = max(length(fwidth(texCoord) * atlasSize) * 0.7071, 0.0001);

    float distancePx = (0.5 - field) * 2.0 * spread / texelsPerPixel;
    float alpha = coverage(distancePx);

    if (glow > 0.0) {
        float halo = clamp(field * 2.0, 0.0, 1.0);
        alpha = max(alpha, halo * halo * glow + ditherOffset(gl_FragCoord.xy));
    }

    vec4 color = vec4(vertexColor.rgb, vertexColor.a * alpha);
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}

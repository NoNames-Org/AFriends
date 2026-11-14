#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <blooddesigner:common.glsl>

const float SCALE = 64.0;
const int SOFT_FLAG = 16384;

layout(location = 0) in vec4 vertexColor;

layout(location = 1) in vec2 localPos;

layout(location = 2) flat in ivec2 params;

layout(location = 3) flat in vec2 quadHalfSize;

layout(location = 0) out vec4 fragColor;

float roundedBox(vec2 point, vec2 halfSize, float radius) {
    vec2 q = abs(point) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    bool soft = params.y >= SOFT_FLAG;
    float softness = soft ? float(params.y - SOFT_FLAG) / SCALE : 0.0;
    float stroke = soft ? 0.0 : float(params.y) / SCALE;

    vec2 halfSize = quadHalfSize - softness;
    float radius = min(float(params.x) / SCALE, min(halfSize.x, halfSize.y));
    float distance = roundedBox(localPos, halfSize, radius);

    float alpha;
    if (soft) {
        float fade = clamp(0.5 - distance / (2.0 * softness), 0.0, 1.0);
        fade = fade * fade * (3.0 - 2.0 * fade);
        alpha = fade * fade;
    } else {
        float pixel = max(fwidth(localPos.x), 0.0001);
        alpha = coverage(distance / pixel);
        if (stroke > 0.0) {
            alpha *= coverage(-(distance + stroke) / pixel);
        }
    }

    float dither = ditherOffset(gl_FragCoord.xy);
    vec4 color = vec4(vertexColor.rgb + dither, vertexColor.a * alpha);
    if (soft) {
        color.a += dither;
    }
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}

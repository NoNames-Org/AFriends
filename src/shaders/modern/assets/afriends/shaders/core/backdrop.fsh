#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <afriends:common.glsl>

const float PARAM_SCALE = 256.0;

layout(location = 0) in vec4 vertexColor;

layout(location = 1) in vec2 screenPos;

layout(location = 2) flat in ivec2 params;

layout(location = 0) out vec4 fragColor;

void main() {
    float vignette = float(params.x) / PARAM_SCALE;
    float grain = float(params.y) / PARAM_SCALE;

    float edge = smoothstep(0.25, 1.0, length(screenPos - 0.5) * 1.4142);
    float alpha = mix(vertexColor.a, 1.0, vignette * edge);

    float noise = screenNoise(gl_FragCoord.xy) - 0.5;
    vec3 color = vertexColor.rgb + noise * grain * 0.08;
    fragColor = vec4(color, alpha + noise / 255.0) * ColorModulator;
}

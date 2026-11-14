#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform KawaseConfig {
    float Offset;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 offset = Offset * (InSize.y / 1080.0) / InSize;

    vec4 sum = texture(InSampler, texCoord + vec2(offset.x, offset.y));
    sum += texture(InSampler, texCoord + vec2(-offset.x, offset.y));
    sum += texture(InSampler, texCoord + vec2(offset.x, -offset.y));
    sum += texture(InSampler, texCoord + vec2(-offset.x, -offset.y));
    fragColor = sum * 0.25;
}

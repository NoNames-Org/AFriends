#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <afriends:common.glsl>

const float GLOW_SCALE = 256.0;

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 uv;

flat in ivec2 params;

out vec4 fragColor;

void main() {
    float field = texture(Sampler0, uv).r;
    float spread = float(params.x);
    float glow = float(params.y) / GLOW_SCALE;

    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    float texelsPerPixel = max(length(fwidth(uv) * atlasSize) * 0.7071, 0.0001);

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

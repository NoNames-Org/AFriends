#ifndef AFRIENDS_COMMON_GLSL
#define AFRIENDS_COMMON_GLSL

float coverage(float distancePx) {
    return clamp(0.5 - distancePx, 0.0, 1.0);
}

float screenNoise(vec2 fragCoord) {
    return fract(52.9829189 * fract(dot(fragCoord, vec2(0.06711056, 0.00583715))));
}

float ditherOffset(vec2 fragCoord) {
    return (screenNoise(fragCoord) - 0.5) / 255.0;
}

#endif

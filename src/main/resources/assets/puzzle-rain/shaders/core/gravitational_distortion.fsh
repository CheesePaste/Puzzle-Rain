#version 150

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float Time;
uniform float GravityCenters[30];
uniform int GravityCenterCount;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec2 distortedUV = uv;

    // 应用所有引力中心的扭曲
    for (int i = 0; i < GravityCenterCount; i++) {
        int baseIndex = i * 3;
        vec2 centerUV = vec2(GravityCenters[baseIndex] / ScreenSize.x,
                           1.0 - GravityCenters[baseIndex + 1] / ScreenSize.y);
        float radius = GravityCenters[baseIndex + 2];

        // 计算到引力中心的距离
        vec2 toCenter = centerUV - uv;
        float dist = length(toCenter);

        if (dist < radius && dist > 0.0) {
            // 引力扭曲公式
            float strength = (radius - dist) / radius;
            strength = strength * strength * 0.05; // 非线性衰减

            // 添加脉动效果
            strength *= (1.0 + sin(Time * 2.0 + dist * 10.0) * 0.1);

            // 应用扭曲
            distortedUV += normalize(toCenter) * strength;
        }
    }

    // 采样扭曲后的颜色
    vec4 color = texture(Sampler0, distortedUV);

    // 添加色差效果
    if (distortedUV != uv) {
        vec4 colorRed = texture(Sampler0, distortedUV + vec2(0.001, 0.0));
        vec4 colorBlue = texture(Sampler0, distortedUV - vec2(0.001, 0.0));
        color.r = colorRed.r;
        color.b = colorBlue.b;

        // 增强对比度
        color.rgb = mix(color.rgb, color.rgb * 1.2, 0.1);
    }

    fragColor = color * vertexColor;
}
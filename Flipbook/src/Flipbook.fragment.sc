$input v_position, v_texcoord0, atlasFlag, v_uv_from, v_uv_to

#include <../../include/bgfx_compute.sh>

uniform vec4 VBlendControl;

#define CUSTOM_TEXTURE_RESOLUTION ivec2(3072, 2048)

SAMPLER2D_AUTOREG(s_BlitTexture);

void main() {
    if (bool(atlasFlag)) {
#       if BGFX_SHADER_LANGUAGE_GLSL
            highp vec2 uv = (v_texcoord0 - 1.0) * u_viewRect.zw / vec2(CUSTOM_TEXTURE_RESOLUTION) + 1.0;
#       else
            highp vec2 uv = vec2(v_texcoord0.x - 1.0, -v_texcoord0.y) * u_viewRect.zw / vec2(CUSTOM_TEXTURE_RESOLUTION) + 1.0;
#       endif

        if (any(lessThan(uv, vec2(0.0, 0.0)))) discard;

        gl_FragColor = texture2D(s_BlitTexture, uv);
    } else {
        vec4 color_from = texture2D(s_BlitTexture, v_uv_from);
        vec4 color_to = texture2D(s_BlitTexture, v_uv_to);
        vec4 color = color_from;
        
        if (color_from.a < 0.01) {
            color = color_to;
        } else if (color_to.a >= 0.01) {
            color = mix(color_from, color_to, VBlendControl.z);
        }

        gl_FragColor = color;
    }
}
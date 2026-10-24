package com.ayman.ecolift.ui.theme

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.ShaderBrush

/**
 * Loaded only on API 33 and above. Keeping the RuntimeShader reference in this file
 * means an API 26 process never loads the class.
 */
internal object QuietDepthShader {
    private const val AGSL = """
        uniform float2 resolution;
        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            half3 top = half3(0.1098, 0.0941, 0.0863);
            half3 lower = half3(0.0824, 0.0706, 0.0627);
            half3 color = mix(top, lower, uv.y);
            float dx = uv.x - 0.5;
            float dy = uv.y - 0.55;
            float vig = smoothstep(0.85, 0.25, length(float2(dx * 1.15, dy)));
            color = mix(color * 0.82, color, vig);
            return half4(color, 1.0);
        }
    """

    fun brush(width: Float, height: Float): ShaderBrush? {
        if (width <= 0f || height <= 0f) return null
        return try {
            val shader = RuntimeShader(AGSL)
            shader.setFloatUniform("resolution", width, height)
            ShaderBrush(shader)
        } catch (error: IllegalArgumentException) {
            null
        }
    }
}

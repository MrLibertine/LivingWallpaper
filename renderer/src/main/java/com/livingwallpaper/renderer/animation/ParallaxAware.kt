package com.livingwallpaper.renderer.animation

/**
 * 支持视差的场景需要实现的接口。
 * 壁纸 Engine 只在场景实现该接口时才注册传感器，避免无谓的功耗。
 */
interface ParallaxAware {
    fun setParallaxEnabled(enabled: Boolean)
    fun setTilt(tiltX: Float, tiltY: Float)
}

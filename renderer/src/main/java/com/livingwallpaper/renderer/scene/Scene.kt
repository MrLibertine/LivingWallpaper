package com.livingwallpaper.renderer.scene

import com.livingwallpaper.core.model.SceneConfig

/**
 * 场景渲染接口。所有方法都必须在持有 GL 上下文的渲染线程调用。
 *
 * 生命周期：initialize → (resize / applyConfig / update / render)* → release
 *
 * 注：[resize] 是计划书 3.5 接口之外补充的方法：视口与分辨率相关 uniform
 * 必须在尺寸变化时更新，无法由 [update] 或 [render] 表达。
 */
interface Scene {
    /** 创建 GL 资源（着色器、VBO/VAO、纹理）。 */
    fun initialize()

    /** Surface 尺寸变化。 */
    fun resize(width: Int, height: Int)

    /** 应用配置快照。传入的配置应已由调用方归一化。 */
    fun applyConfig(config: SceneConfig)

    /** 推进动画状态，deltaTimeSeconds 已由渲染循环夹紧。 */
    fun update(deltaTimeSeconds: Float)

    /** 绘制一帧。 */
    fun render()

    /** 释放 GL 资源。 */
    fun release()
}

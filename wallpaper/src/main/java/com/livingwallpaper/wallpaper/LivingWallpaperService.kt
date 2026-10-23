package com.livingwallpaper.wallpaper

import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.livingwallpaper.core.data.SceneRepository
import com.livingwallpaper.core.data.SceneRepositoryProvider
import com.livingwallpaper.renderer.animation.ParallaxAware
import com.livingwallpaper.renderer.gl.GLRenderer
import com.livingwallpaper.renderer.scene.Scene
import com.livingwallpaper.renderer.scene.SceneFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android 系统动态壁纸服务。
 *
 * 生命周期完全跟随 WallpaperService.Engine 回调，不在主线程绘制：
 * - Surface 创建 → 用仓库快照启动 GL 线程；
 * - 配置变化 → 通过 GLRenderer 的线程安全队列下发，场景类型变化时重建场景；
 * - 可见性变化 → 恢复/暂停渲染，同时注册/注销视差传感器；
 * - Surface 销毁 → 停止渲染线程并释放 EGL 资源。
 */
class LivingWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = LivingWallpaperEngine()

    inner class LivingWallpaperEngine : Engine() {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private var renderer: GLRenderer? = null
        private var scene: Scene? = null
        private var parallaxTarget: ParallaxAware? = null
        private var parallaxSensor: ParallaxSensor? = null
        private var configJob: Job? = null
        private var repository: SceneRepository? = null

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            Log.i(TAG, "Engine onCreate")
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            Log.i(TAG, "onSurfaceCreated")

            val repository = SceneRepositoryProvider.get(applicationContext)
            this.repository = repository
            val initial = repository.snapshot()

            val created = SceneFactory.create(initial.sceneType, applicationContext)
            scene = created

            if (created is ParallaxAware) {
                parallaxTarget = created
                parallaxSensor = ParallaxSensor(applicationContext) { tiltX, tiltY ->
                    parallaxTarget?.setTilt(tiltX, tiltY)
                }
            }

            val frame = holder.surfaceFrame
            renderer = GLRenderer(
                sceneFactory = { type -> SceneFactory.create(type, applicationContext) },
                initialConfig = initial
            ).also { it.start(holder.surface, frame.width(), frame.height()) }

            // 配置在 App 中被修改后，壁纸实时跟随；切换场景类型由渲染器在 GL 线程内完成
            configJob?.cancel()
            configJob = scope.launch {
                repository.config.collect { config -> renderer?.applyConfig(config) }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            Log.i(TAG, "onSurfaceChanged ${width}x${height}, format=$format")
            renderer?.resize(width, height)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            // 渲染循环持续运行，尺寸变化后下一帧即可刷新；这里只保证处于可见状态。
            renderer?.setVisible(true)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Log.i(TAG, "onVisibilityChanged visible=$visible")
            renderer?.setVisible(visible)
            if (visible) {
                parallaxSensor?.register()
            } else {
                parallaxSensor?.unregister()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            Log.i(TAG, "onSurfaceDestroyed")
            stopRendering()
        }

        override fun onDestroy() {
            super.onDestroy()
            Log.i(TAG, "Engine onDestroy")
            stopRendering()
            scope.cancel()
        }

        private fun stopRendering() {
            configJob?.cancel()
            configJob = null
            parallaxSensor?.unregister()
            parallaxSensor = null
            parallaxTarget = null
            renderer?.stop()
            renderer = null
            scene = null
            repository = null
        }
    }

    private companion object {
        const val TAG = "LivingWallpaperService"
    }
}

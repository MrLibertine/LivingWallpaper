package com.livingwallpaper.wallpaper

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * 视差传感器：读取加速度计，把重力方向换算成 -1..1 的归一化倾角。
 *
 * 只在壁纸可见时注册，不可见时立即注销（计划书 2.3：暂停非必要传感器监听）。
 */
class ParallaxSensor(
    context: Context,
    private val onTilt: (Float, Float) -> Unit
) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var registered = false

    /** 注册成功返回 true；设备无加速度计或注册失败返回 false。 */
    fun register(): Boolean {
        if (registered) return true
        val manager = sensorManager
        val sensor = accelerometer
        if (manager == null || sensor == null) {
            Log.w(TAG, "设备没有可用的加速度计，视差将保持关闭")
            return false
        }
        registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        return registered
    }

    fun unregister() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        if (event.values.size < 2) return
        val tiltX = (event.values[0] / GRAVITY).coerceIn(-1f, 1f)
        val tiltY = (event.values[1] / GRAVITY).coerceIn(-1f, 1f)
        onTilt(tiltX, tiltY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val TAG = "ParallaxSensor"
        const val GRAVITY = 9.81f
    }
}

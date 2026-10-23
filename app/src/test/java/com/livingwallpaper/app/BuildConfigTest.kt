package com.livingwallpaper.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P1 唯一适用的单元测试：校验写入 BuildConfig 的 SDK 版本与需求一致。
 * 这些值由 app/build.gradle.kts 注入，改动构建脚本时能被立即发现。
 */
class BuildConfigTest {

    @Test
    fun minSdk_matchesRequirement() {
        assertEquals(29, BuildConfig.MIN_SDK_VERSION)
    }

    @Test
    fun compileSdk_matchesTargetSdk() {
        assertEquals(BuildConfig.TARGET_SDK_VERSION, BuildConfig.COMPILE_SDK_VERSION)
    }

    @Test
    fun applicationId_isStable() {
        assertEquals("com.livingwallpaper.app", BuildConfig.APPLICATION_ID)
    }
}

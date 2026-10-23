package com.livingwallpaper.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.livingwallpaper.core.model.SceneConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * 场景配置仓库：UI 与壁纸引擎的唯一可信来源。
 */
interface SceneRepository {
    /** 配置流，任何一端修改都会推送到这里。 */
    val config: Flow<SceneConfig>

    /** 同步快照。供无法挂起的地方（如壁纸 Surface 回调）读取当前值。 */
    fun snapshot(): SceneConfig

    /** 以当前配置为基础做一次原子更新，返回写入后的配置。 */
    suspend fun update(transform: (SceneConfig) -> SceneConfig): SceneConfig
}

private val Context.sceneConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "scene_config"
)

class DataStoreSceneRepository(context: Context) : SceneRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore = context.applicationContext.sceneConfigDataStore
    private val writeLock = Mutex()

    @Volatile
    private var latest: SceneConfig = SceneConfig.DEFAULT.clamped()

    /**
     * Eagerly 启动：对象创建后立刻读取磁盘，
     * 因此 [snapshot] 在极短时间内就能拿到真实值，而不是一直停留在默认值。
     */
    private val state = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            SceneConfigPreferences.read(preferences).also { latest = it }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, SceneConfig.DEFAULT.clamped())

    override val config: Flow<SceneConfig> = state

    override fun snapshot(): SceneConfig = latest

    override suspend fun update(transform: (SceneConfig) -> SceneConfig): SceneConfig =
        writeLock.withLock {
            var result = SceneConfig.DEFAULT.clamped()
            dataStore.updateData { preferences ->
                val next = transform(SceneConfigPreferences.read(preferences)).clamped()
                val mutable = preferences.toMutablePreferences()
                SceneConfigPreferences.write(mutable, next)
                result = next
                mutable
            }
            latest = result
            result
        }
}

/**
 * 壁纸 Service 没有依赖注入框架，这里用一个进程内单例保证
 * UI 与壁纸读到同一份配置。
 */
object SceneRepositoryProvider {

    @Volatile
    private var instance: SceneRepository? = null

    fun get(context: Context): SceneRepository =
        instance ?: synchronized(this) {
            instance ?: DataStoreSceneRepository(context.applicationContext).also {
                instance = it
            }
        }
}

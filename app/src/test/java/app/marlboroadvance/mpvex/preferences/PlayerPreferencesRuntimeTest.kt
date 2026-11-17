package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.Preference
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPreferencesRuntimeTest {
  @Test
  fun `hide preference reads legacy values safely and writes only normalized milliseconds`() = runBlocking {
    val store = Store()
    val stored = store.getInt("player_time_to_disappear", 4000)
    val preference = PlayerPreferences(store).playerTimeToDisappear
    assertEquals(5000, preference.get())
    assertEquals(5000, preference.changes().first())
    preference.set(0)
    assertEquals(5000, stored.get())
    preference.set(-1)
    assertEquals(-1, stored.get())
    assertEquals(-1, preference.changes().first())
    preference.delete()
    assertEquals(5000, preference.get())
  }

  @Test
  fun `all eight moved gesture settings keep their exact original keys`() {
    val store = Store()
    PlayerPreferences(store)
    val gestures = setOf(
      "hold_for_multiple_speed", "show_dynamic_speed_overlay", "gestures_brightness", "volume_brightness",
      "pinch_to_zoom_gesture", "horizontal_swipe_to_seek", "horizontal_swipe_sensitivity", "haptic_feedback_enabled",
    )
    assertEquals(gestures, store.values.keys.intersect(gestures))
  }

  @Test
  fun `moved seeking settings retain existing values and original storage keys`() {
    val store = Store()
    store.getBoolean("use_precise_seeking").set(true)
    store.getInt("custom_skip_duration").set(137)

    val preferences = PlayerPreferences(store)
    assertEquals(true, preferences.usePreciseSeeking.get())
    assertEquals(137, preferences.customSkipDuration.get())

    preferences.usePreciseSeeking.set(false)
    preferences.customSkipDuration.set(65)
    val recreated = PlayerPreferences(store)
    assertEquals(false, recreated.usePreciseSeeking.get())
    assertEquals(65, recreated.customSkipDuration.get())
    assertEquals(false, store.getBoolean("use_precise_seeking").get())
    assertEquals(65, store.getInt("custom_skip_duration").get())
  }

  private class Value<T>(private val default: T) : Preference<T> {
    private val current = MutableStateFlow(default)
    override fun get() = current.value
    override fun set(value: T) { current.value = value }
    override fun delete() { current.value = default }
    override fun changes(): Flow<T> = current
  }

  private class Store : PreferenceStore {
    val values = mutableMapOf<String, Preference<*>>()
    @Suppress("UNCHECKED_CAST")
    private fun <T> value(key: String, default: T): Preference<T> =
      values.getOrPut(key) { Value(default) } as Preference<T>
    override fun getString(key: String, defaultValue: String) = value(key, defaultValue)
    override fun getLong(key: String, defaultValue: Long) = value(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int) = value(key, defaultValue)
    override fun getFloat(key: String, defaultValue: Float) = value(key, defaultValue)
    override fun getBoolean(key: String, defaultValue: Boolean) = value(key, defaultValue)
    override fun getStringSet(key: String, defaultValue: Set<String>) = value(key, defaultValue)
    override fun <T> getObject(
      key: String,
      defaultValue: T,
      serializer: (T) -> String,
      deserializer: (String) -> T,
    ) = value(key, defaultValue)
  }
}

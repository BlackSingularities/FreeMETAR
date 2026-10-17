package com.freemetar.app.data.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** Stan fizycznego kontrolera (gamepad / joystick Bluetooth lub USB) podłączonego do telefonu. */
data class GamepadState(
    val deviceName: String? = null,
    /** −1…1: lotki (lewa gałka X / drążek X), ster wysokości (lewa gałka Y / drążek Y, do siebie = +). */
    val roll: Double = 0.0,
    val pitch: Double = 0.0,
    /** −1…1: ster kierunku (twist drążka albo prawa gałka X). */
    val yaw: Double = 0.0,
    val lastButton: Int? = null,
    val lastButtonAt: Long = 0,
)

/**
 * Wejście z kontrolera przechwytywane w aktywności (onGenericMotionEvent / dispatchKeyEvent).
 * Samo nie wysyła nic do symulatora — robi to panel drążka, gdy użytkownik włączy sterowanie kontrolerem.
 */
object Gamepad {
    private val _state = MutableStateFlow(GamepadState())
    val state = _state.asStateFlow()

    private fun isController(source: Int) =
        source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD

    /** Strefa martwa na środku gałki, potem liniowo do pełnego wychylenia. */
    fun dead(v: Float, zone: Float = 0.08f): Double = when {
        abs(v) < zone -> 0.0
        v > 0 -> ((v - zone) / (1 - zone)).toDouble()
        else -> ((v + zone) / (1 - zone)).toDouble()
    }

    /** Zwraca true, gdy zdarzenie pochodziło z kontrolera (aktywność nie przekazuje go dalej). */
    fun onMotion(e: MotionEvent): Boolean {
        if (!isController(e.source) || e.action != MotionEvent.ACTION_MOVE) return false
        val dev = e.device
        fun axis(a: Int): Float {
            val range = dev?.getMotionRange(a, e.source) ?: return 0f
            val v = e.getAxisValue(a)
            return if (abs(v) <= range.flat) 0f else v
        }
        // Drążki lotnicze mają twist na RZ; gamepady — prawą gałkę na Z/RZ
        val yawAxis = if (dev?.getMotionRange(MotionEvent.AXIS_RZ, e.source) != null) MotionEvent.AXIS_RZ else MotionEvent.AXIS_Z
        _state.value = _state.value.copy(
            deviceName = dev?.name,
            roll = dead(axis(MotionEvent.AXIS_X)),
            // Oś Y: do przodu = ujemna; ster wysokości „do siebie” = +
            pitch = -dead(axis(MotionEvent.AXIS_Y)),
            yaw = dead(axis(yawAxis)),
        )
        return true
    }

    fun onKey(e: KeyEvent): Boolean {
        if (!isController(e.source) || e.action != KeyEvent.ACTION_DOWN || e.repeatCount > 0) return false
        if (!KeyEvent.isGamepadButton(e.keyCode)) return false
        _state.value = _state.value.copy(deviceName = e.device?.name, lastButton = e.keyCode, lastButtonAt = System.currentTimeMillis())
        return true
    }

    /** Podłączone kontrolery (nazwy), do pokazania w panelu. */
    fun connectedNames(): List<String> = InputDevice.getDeviceIds().toList().mapNotNull { id ->
        InputDevice.getDevice(id)?.takeIf { isController(it.sources) && !it.isVirtual }?.name
    }.distinct()

    /** Przypisanie przycisków kontrolera do komend Link (tylko z listy dozwolonych). */
    val buttonCommands: Map<Int, String> = mapOf(
        KeyEvent.KEYCODE_BUTTON_Y to "GEAR_TOGGLE",
        KeyEvent.KEYCODE_BUTTON_L1 to "FLAPS_DECR",
        KeyEvent.KEYCODE_BUTTON_R1 to "FLAPS_INCR",
        KeyEvent.KEYCODE_BUTTON_X to "AP_MASTER",
        KeyEvent.KEYCODE_BUTTON_B to "PARKING_BRAKES",
        KeyEvent.KEYCODE_BUTTON_SELECT to "SPOILERS_TOGGLE",
        KeyEvent.KEYCODE_DPAD_UP to "ELEV_TRIM_DN",
        KeyEvent.KEYCODE_DPAD_DOWN to "ELEV_TRIM_UP",
    )
}

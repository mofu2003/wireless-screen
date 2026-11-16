package app.opendisplay.android.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager

/**
 * Injects the Mac's pointer and keyboard into whichever app has focus.
 * Android gives ordinary apps no global input API, so this mirrors what
 * scrcpy does through ADB, using the accessibility APIs instead.
 */
class InputAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var remoteInputRequested = false

        @Volatile
        var instance: InputAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val expected = "${context.packageName}/${InputAccessibilityService::class.java.name}"
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }

    private var downX = 0.0
    private var downY = 0.0
    private var moved = false
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var remoteInputActive = false
    @Volatile private var imeRequestDeadline = 0L

    override fun onServiceConnected() {
        instance = this
        applyKeyboardMode(remoteInputRequested)
    }

    override fun onDestroy() {
        applyKeyboardMode(false)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!remoteInputActive || imeRequestDeadline == 0L) return
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> requestImeWhenReady(0)
        }
    }

    override fun onInterrupt() = Unit

    fun handleTouch(phase: String, x: Double, y: Double) {
        when (phase) {
            "began" -> {
                downX = x
                downY = y
                moved = false
            }
            "moved" -> moved = true
            "ended" -> {
                val metrics = resources.displayMetrics
                val sx = (downX * metrics.widthPixels).toFloat()
                val sy = (downY * metrics.heightPixels).toFloat()
                val ex = (x * metrics.widthPixels).toFloat()
                val ey = (y * metrics.heightPixels).toFloat()
                val distance = Math.hypot((ex - sx).toDouble(), (ey - sy).toDouble())
                if (moved || distance > 12.0) {
                    swipe(sx, sy, ex, ey)
                } else {
                    tap(sx, sy)
                    // The tap may have focused an EditText. Focus arrives on
                    // the app's schedule, and slow/OEM builds regularly miss
                    // a single fixed delay, so retry until a focused editable
                    // node appears (or the window changes away).
                    imeRequestDeadline = SystemClock.uptimeMillis() + 2000
                    main.postDelayed({ requestImeWhenReady(0) }, 80)
                }
            }
        }
    }

    /** Called by SenderSession while a Mac is controlling this device. */
    fun setRemoteInputActive(active: Boolean) {
        remoteInputRequested = active
        main.post { applyKeyboardMode(active) }
    }

    fun typeText(text: String) {
        if (text.isEmpty()) return
        val node = editableFocus()
        if (node != null) {
            val old = node.text?.toString().orEmpty()
            if (setText(node, old + text)) {
                requestIme(node, force = false)
                return
            }
        }
        paste(text)
    }

    private fun paste(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(app.opendisplay.android.ProductInfo.DISPLAY_NAME, text))
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    fun pressKey(key: String) {
        val node = editableFocus()
        when (key) {
            "backspace" -> {
                val old = node?.text?.toString().orEmpty()
                if (node != null && old.isNotEmpty()) setText(node, old.dropLast(1))
            }
            "enter" -> {
                if (Build.VERSION.SDK_INT >= 30 && node != null) {
                    node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                } else if (node != null) {
                    setText(node, node.text?.toString().orEmpty() + "\n")
                }
            }
            "escape" -> performGlobalAction(GLOBAL_ACTION_BACK)
        }
        if (key != "escape") requestIme(node, force = false)
    }

    private fun applyKeyboardMode(active: Boolean) {
        remoteInputActive = active
        if (Build.VERSION.SDK_INT < 24) return
        val mode = when {
            !active -> AccessibilityService.SHOW_MODE_AUTO
            Build.VERSION.SDK_INT >= 29 -> AccessibilityService.SHOW_MODE_IGNORE_HARD_KEYBOARD
            else -> AccessibilityService.SHOW_MODE_AUTO
        }
        softKeyboardController?.setShowMode(mode)
    }

    private fun requestImeWhenReady(attempt: Int) {
        if (!remoteInputActive || imeRequestDeadline == 0L) return
        if (SystemClock.uptimeMillis() > imeRequestDeadline) {
            imeRequestDeadline = 0L
            return
        }
        val node = editableFocus()
        if (node != null) {
            requestIme(node, force = true)
            imeRequestDeadline = 0L
            return
        }
        if (attempt < 19) {
            main.postDelayed({ requestImeWhenReady(attempt + 1) }, 100)
        }
    }

    private fun requestIme(node: AccessibilityNodeInfo?, force: Boolean) {
        if (!remoteInputActive || node == null) return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        // isAcceptingText stays true while a hardware keyboard has an active
        // input connection, even when the soft keyboard is hidden. A user
        // tap must therefore force the focus/click path once; injected text
        // keeps the cheap check so it does not tap the field per character.
        if (!force && imm?.isAcceptingText == true) return

        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) tap(bounds.exactCenterX(), bounds.exactCenterY())
    }

    private fun editableFocus(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        return if (node.isEditable) node else null
    }

    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        dispatch(path, 60)
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        dispatch(path, 220)
    }

    private fun dispatch(path: Path, durationMs: Long) {
        if (Build.VERSION.SDK_INT < 24) return
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}

package app.opendisplay.android.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import app.opendisplay.android.Log
import app.opendisplay.android.R
import kotlin.math.roundToInt

/**
 * Floating keyboard button for the receiver. The focused editor is an
 * invisible 1x1 mirror: its text drives deltas to the Mac, while composing
 * spans (pinyin, romaji, candidates) stay local until the input method commits.
 */
internal class RemoteKeyboard(
    private val activity: Activity,
    private val root: FrameLayout,
    private val output: Output,
) {
    interface Output {
        fun text(text: String)
        fun key(key: String)
    }

    private val button = FloatingActionButton(activity)
    private var lastCommitted = ""
    private var resetting = false
    private var imeVisible = false
    private var available = false

    private val input = object : AppCompatEditText(activity) {
        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN
            val base = super.onCreateInputConnection(outAttrs) ?: return null
            return object : InputConnectionWrapper(base, false) {
                override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                    val handled = super.commitText(text, newCursorPosition)
                    reconcile()
                    return handled
                }

                override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                    val handled = super.setComposingText(text, newCursorPosition)
                    reconcile()
                    return handled
                }

                override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                    val handled = super.deleteSurroundingText(beforeLength, afterLength)
                    reconcile()
                    return handled
                }

                override fun performEditorAction(action: Int): Boolean {
                    output.key("enter")
                    return true
                }

                override fun sendKeyEvent(event: KeyEvent): Boolean {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_ESCAPE -> {
                                output.key("escape")
                                return true
                            }
                            KeyEvent.KEYCODE_TAB -> {
                                output.key("tab")
                                return true
                            }
                        }
                    }
                    val handled = super.sendKeyEvent(event)
                    reconcile()
                    return handled
                }
            }
        }
    }

    init {
        configureButton()
        configureInput()
        root.addView(input, FrameLayout.LayoutParams(1, 1))
        root.addView(button, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = dp(20)
            bottomMargin = dp(24)
        })

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val visible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeVisible && !visible) resetMirror()
            imeVisible = visible
            insets
        }
    }

    fun refresh(streaming: Boolean, inputAvailable: Boolean) {
        available = streaming && inputAvailable
        button.visibility = if (available) View.VISIBLE else View.GONE
        if (!streaming && imeVisible) hide()
    }

    fun hide() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        imeVisible = false
        resetMirror()
    }

    private fun configureButton() {
        button.setImageResource(R.drawable.ic_keyboard)
        button.imageTintList = ColorStateList.valueOf(Color.WHITE)
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.od_accent))
        button.contentDescription = activity.getString(R.string.remote_keyboard)
        button.visibility = View.GONE
        button.setOnClickListener {
            if (available) show()
        }
    }

    private fun configureInput() {
        input.isFocusable = true
        input.isFocusableInTouchMode = true
        input.showSoftInputOnFocus = true
        input.isCursorVisible = false
        input.setTextColor(Color.TRANSPARENT)
        input.setBackgroundColor(Color.TRANSPARENT)
        input.includeFontPadding = false
        input.setPadding(0, 0, 0, 0)
        input.alpha = 0.01f
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        if (Build.VERSION.SDK_INT >= 26) {
            input.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(editable: Editable) {
                reconcile()
            }
        })
    }

    private fun show() {
        if (!imeVisible) resetMirror()
        input.requestFocus()
        showIme(attempt = 0)
    }

    private fun showIme(attempt: Int) {
        input.postDelayed({
            if (!available || imeVisible) return@postDelayed
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            if (imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) == true) {
                imeVisible = true
                Log.i(TAG, "remote keyboard shown")
            } else if (attempt < 2) {
                input.requestFocus()
                showIme(attempt + 1)
            }
        }, if (attempt == 0) 40L else 100L)
    }

    private fun resetMirror() {
        if (input.text?.isEmpty() != false && lastCommitted.isEmpty()) return
        resetting = true
        input.text?.clear()
        lastCommitted = ""
        resetting = false
    }

    private fun committedText(editable: Editable): String {
        val length = editable.length
        val start = BaseInputConnection.getComposingSpanStart(editable)
        val end = BaseInputConnection.getComposingSpanEnd(editable)
        if (start < 0 || end < 0 || end > length || start >= end) return editable.toString()
        return editable.toString().removeRange(start, end)
    }

    private fun reconcile() {
        if (resetting) return
        val editable = input.text ?: return
        val committed = committedText(editable)
        if (committed == lastCommitted) return
        val change = RemoteTextDiff.between(lastCommitted, committed)
        lastCommitted = committed
        repeat(change.deletions) { output.key("backspace") }
        sendTyped(change.inserted)
    }

    private fun sendTyped(text: String) {
        if (text.isEmpty()) return
        val pending = StringBuilder()
        fun flush() {
            if (pending.isNotEmpty()) {
                output.text(pending.toString())
                pending.setLength(0)
            }
        }
        for (character in text) {
            when (character) {
                '\n', '\r' -> {
                    flush()
                    output.key("enter")
                }
                '\t' -> {
                    flush()
                    output.key("tab")
                }
                else -> pending.append(character)
            }
        }
        flush()
    }

    private fun dp(value: Int) =
        (value * activity.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TAG = "WirelessScreen"
    }
}

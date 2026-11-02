package app.fastie

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Nuvio sign-in. The TV way comes first: show a code, approve it on your phone. Email and password is the fallback.
 */
class SignInActivity : Activity() {
    private lateinit var code: android.widget.TextView
    private lateinit var hint: android.widget.TextView
    private lateinit var status: android.widget.TextView
    private lateinit var form: LinearLayout
    private var polling: Nuvio.TvCode? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        edgeToEdge()
        val side = dp(if (isTv) 72 else 20)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(side, dp(if (isTv) 56 else 72), side, dp(48))
        }
        col.addView(text(T.DISPLAY, s = "Sign in to Nuvio"))
        col.addView(text(T.BODY, C.variant, s = "Your addons, My List and Continue Watching come with you. Fastie is an unofficial Nuvio client.")
            .apply { setPadding(0, dp(10), 0, dp(28)) })

        hint = text(T.BODY, s = "Getting a sign-in code…")
        col.addView(hint)
        code = text(T.DISPLAY).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = if (isTv) 56f else 44f
            letterSpacing = 0.08f
            setTextColor(C.onPrimaryContainer)
            setPadding(dp(32), dp(16), dp(32), dp(16))
            background = rounded(C.primaryContainer, dp(Shape.XL).toFloat())
        }
        col.addView(code, lp(-2, -2).apply { topMargin = dp(16); bottomMargin = dp(16) })
        val buttons = LinearLayout(this)
        if (!isTv) buttons.addView(button("Open link", primary = true) {
            polling?.let { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) } }
        }, lp(-2, -2).apply { marginEnd = dp(10) })
        val useEmail = button("Use email and password") { form.visibility = View.VISIBLE; form.getChildAt(0).requestFocus() }
        buttons.addView(useEmail, lp(-2, -2))
        if (isTv) useEmail.requestFocus()
        col.addView(buttons, lp().apply { topMargin = dp(12) })

        form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(0, dp(28), 0, 0) }
        val email = field("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = field("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        form.addView(email, lp(if (isTv) dp(480) else -1, -2).apply { bottomMargin = dp(10) })
        form.addView(password, lp(if (isTv) dp(480) else -1, -2).apply { bottomMargin = dp(14) })
        form.addView(button("Sign in", primary = true) {
            status.text = "Signing in…"
            load({ Nuvio.signIn(this, email.text.toString(), password.text.toString()) }) { r ->
                r.onSuccess { finishSignIn() }.onFailure {
                    status.text = if (it is HttpException && it.code in 400..401) "That email and password didn't match a Nuvio account."
                    else "Couldn't reach Nuvio. Check the connection and try again."
                }
            }
        }, lp(-2, -2))
        col.addView(form)
        status = text(T.BODY, C.variant).apply { setPadding(0, dp(20), 0, 0) }
        col.addView(status)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(C.ground)
            addView(col, FrameLayout.LayoutParams(-1, -2))
        })
        startCode()
    }

    private fun field(label: String, type: Int) = EditText(this).apply {
        hint = label
        inputType = type
        isSingleLine = true
        setTextColor(C.onSurface)
        setHintTextColor(C.variant)
        setPadding(dp(20), dp(16), dp(20), dp(16))
        background = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(C.containerHigh, dp(Shape.L).toFloat(), dp(2), C.primary))
            addState(intArrayOf(), rounded(C.containerHigh, dp(Shape.L).toFloat()))
        }
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun startCode() {
        load({ Nuvio.startTvLogin(this) }) { r ->
            r.onSuccess { c ->
                polling = c
                hint.text = "On your phone, open ${c.url.substringBefore('?').removePrefix("https://")} and enter this code:"
                code.text = c.userCode.uppercase().let { if (it.length == 6) "${it.take(3)}-${it.drop(3)}" else it }
                poll(c)
            }.onFailure {
                hint.text = "Couldn't get a sign-in code. Use your email and password instead."
                form.visibility = View.VISIBLE
            }
        }
    }

    private fun poll(c: Nuvio.TvCode) {
        code.postDelayed({
            if (isFinishing || polling !== c) return@postDelayed
            load({ Nuvio.pollTvLogin(this, c) }) { r ->
                r.onSuccess { done -> if (done) finishSignIn() else poll(c) }
                    .onFailure { e -> if (e is IllegalStateException) { status.text = e.message; startCode() } else poll(c) }
            }
        }, c.intervalMs)
    }

    private fun finishSignIn() {
        polling = null
        status.text = "Signed in. Bringing over your addons and progress…"
        load({ Nuvio.pull(this) }) { r ->
            Nuvio.changed = true
            if (r.isFailure) status.text = "Signed in, but the first sync failed. Try Sync now in Settings."
            finish()
        }
    }

    override fun onDestroy() {
        polling = null
        super.onDestroy()
    }
}

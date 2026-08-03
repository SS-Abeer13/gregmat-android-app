package com.example.gregmatvocabpro.ui.main

import com.example.gregmatvocabpro.MainActivity
import com.example.gregmatvocabpro.NotificationReceiver
import com.example.gregmatvocabpro.R
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.os.Message
import android.app.Dialog
import android.view.ViewGroup
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.webkit.WebViewAssetLoader
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation3.runtime.NavKey

class WebAppInterface(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isTtsInitialized = false

    /** Set by MainScreen right after the WebView is created, so the native
     *  sign-in result can be delivered back into JS via evaluateJavascript. */
    var webView: WebView? = null
    private val bridgeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsInitialized = true
        }
    }

    @JavascriptInterface
    fun speak(text: String) {
        if (isTtsInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AndroidTTS")
        }
    }

    @JavascriptInterface
    fun showNotification(title: String, message: String) {
        com.example.gregmatvocabpro.ReminderScheduler.ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, com.example.gregmatvocabpro.ReminderScheduler.CHANNEL_ID)
            // A proper monochrome silhouette (not the full-color launcher
            // mipmap) — Android renders setSmallIcon as flat white regardless,
            // but a purpose-drawn glyph reads correctly at that size. Resolves
            // per flavor: app/src/bigbook/res/drawable/ic_notification.xml
            // overrides this with a clipboard instead of a book.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        manager.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
    }

    @JavascriptInterface
    fun exitApp() {
        try {
            (context as? android.app.Activity)?.finishAffinity()
        } catch (e: Exception) {
            (context as? android.app.Activity)?.finish()
        }
    }

    @JavascriptInterface
    fun setAppIcon(key: String) {
        try {
            com.example.gregmatvocabpro.IconManager.setAppIcon(context, key)
        } catch (e: Exception) {
            android.util.Log.e("WebAppInterface", "setAppIcon failed: ${e.message}")
        }
    }

    @JavascriptInterface
    fun vibrate(patternJson: String) {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }

            if (!vibrator.hasVibrator()) return

            val arr = org.json.JSONArray(patternJson)
            val timings = LongArray(arr.length()) { i -> arr.getLong(i) }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val effect = android.os.VibrationEffect.createWaveform(timings, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (e: Exception) {
            android.util.Log.e("WebAppInterface", "vibrate failed: ${e.message}")
        }
    }

    @JavascriptInterface
    fun hasNotificationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @JavascriptInterface
    fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val activity = context as? android.app.Activity
            if (activity != null && !hasNotificationPermission()) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    /**
     * Primary entry point: the web app sends its whole reminder set as JSON.
     * Settings are persisted so BootReceiver can restore them after a reboot.
     */
    @JavascriptInterface
    fun setReminders(json: String) {
        try {
            val reminders = com.example.gregmatvocabpro.ReminderScheduler.parse(json)
            com.example.gregmatvocabpro.ReminderScheduler.save(context, reminders)
            com.example.gregmatvocabpro.ReminderScheduler.applyAll(context, reminders)
        } catch (e: Exception) {
            android.util.Log.e("WebAppInterface", "setReminders failed: ${e.message}")
        }
    }

    /** Turn everything off without discarding the user's configured times. */
    @JavascriptInterface
    fun cancelAllReminders() {
        try {
            val saved = com.example.gregmatvocabpro.ReminderScheduler.load(context)
            saved.forEach { com.example.gregmatvocabpro.ReminderScheduler.cancel(context, it.kind) }
        } catch (e: Exception) {
            android.util.Log.e("WebAppInterface", "cancelAllReminders failed: ${e.message}")
        }
    }

    /** Reports whether exact alarms are allowed, so the UI can warn the user. */
    @JavascriptInterface
    fun canScheduleExactAlarms(): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                am.canScheduleExactAlarms()
            } else true
        } catch (e: Exception) {
            false
        }
    }

    /** Opens the system screen where the user can grant exact-alarm access. */
    @JavascriptInterface
    fun openExactAlarmSettings() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:${context.packageName}")
                ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("WebAppInterface", "openExactAlarmSettings failed: ${e.message}")
        }
    }

    /** Legacy single-reminder bridge, kept so an older web build still works. */
    @JavascriptInterface
    fun updateReminderSettings(enabled: Boolean, timeString: String) {
        try {
            val parts = timeString.trim().split(" ")
            val timeParts = parts[0].split(":")
            var hour = timeParts[0].toInt()
            val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
            if (parts.size > 1) {
                val ampm = parts[1].uppercase()
                if (ampm == "PM" && hour < 12) hour += 12
                if (ampm == "AM" && hour == 12) hour = 0
            }

            val reminder = com.example.gregmatvocabpro.Reminder(
                kind = "dailyWords",
                enabled = enabled,
                hour = hour,
                minute = minute,
                days = emptyList(),
                title = "Today's words are waiting",
                body = "Start your daily set and keep the streak alive."
            )
            com.example.gregmatvocabpro.ReminderScheduler.save(context, listOf(reminder))
            com.example.gregmatvocabpro.ReminderScheduler.applyAll(context, listOf(reminder))
        } catch (e: Exception) {
            android.util.Log.e("WebAppInterface", "updateReminderSettings failed: ${e.message}")
        }
    }

    /**
     * Manual "Export Progress" fallback: writes the given text content
     * (a JSON snapshot of the user's progress, built on the JS side) to a
     * real file the user can find outside the app, so they always have a
     * way to pull a point-in-time backup even if cloud sync is unavailable.
     *
     * API 29+ (Q+): saved via MediaStore into the public Downloads folder —
     * no permission needed, shows up in any file manager / Downloads app.
     * Older API levels: saved into the app's own external files directory
     * (no permission needed there either, but only visible via a file
     * manager under Android/data/<package>/files/) to avoid requiring a
     * runtime WRITE_EXTERNAL_STORAGE permission grant flow for what's a
     * rarely-used fallback action.
     *
     * @return the resulting path/location on success, or "" on failure.
     */
    @JavascriptInterface
    fun exportProgressFile(filename: String, content: String): String {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return ""
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                } ?: return ""
                "Downloads/$filename"
            } else {
                val dir = context.getExternalFilesDir(null) ?: return ""
                val file = java.io.File(dir, filename)
                file.writeText(content, Charsets.UTF_8)
                file.absolutePath
            }
        } catch (e: Exception) {
            android.util.Log.e("WebAppInterface", "exportProgressFile failed: ${e.message}")
            ""
        }
    }

    /**
     * Lets the web app check for native Google Sign-In support before
     * deciding whether to call nativeGoogleSignIn() or fall back to
     * signInWithPopup (e.g. when running in a plain browser during dev).
     */
    @JavascriptInterface
    fun isNativeGoogleSignInAvailable(): Boolean = true

    /**
     * Native "Sign in with Google" via Credential Manager. This never opens
     * a popup/redirect through Firebase's hosted auth handler, so it is not
     * subject to the Authorized-domains allowlist that blocks
     * signInWithPopup/signInWithRedirect from the
     * https://appassets.androidplatform.net origin. On success/failure this
     * calls back into JS via window.onNativeGoogleSignIn(idToken) /
     * window.onNativeGoogleSignInError(reason), which App.tsx's
     * nativeGoogleSignIn() helper listens for.
     *
     * @param interactive false = silent-only restore of a previously
     * authorized account. Used for auto-sign-in on app launch: no UI is
     * shown, and it fails quietly (`no_silent_credential`) if the user has
     * never signed in on this device before. true = a real "Sign In" tap:
     * tries the same silent restore first (instant, prompt-free on every
     * visit after the first), and only shows the account picker if there's
     * no previously authorized account yet.
     */
    @JavascriptInterface
    fun nativeGoogleSignIn(interactive: Boolean) {
        val activity = context as? android.app.Activity
        if (activity == null) {
            postToJs("window.onNativeGoogleSignInError && window.onNativeGoogleSignInError('no_activity')")
            return
        }

        bridgeScope.launch {
            try {
                val idToken = try {
                    fetchGoogleIdToken(activity, filterByAuthorizedAccounts = true, autoSelect = true)
                } catch (silentFailure: GetCredentialException) {
                    if (!interactive) throw silentFailure
                    // No previously authorized account on this device yet —
                    // show the picker once so the user can grant access;
                    // every launch after this succeeds silently above.
                    fetchGoogleIdToken(activity, filterByAuthorizedAccounts = false, autoSelect = false)
                }
                postToJs("window.onNativeGoogleSignIn && window.onNativeGoogleSignIn(${JSONObject.quote(idToken)})")
            } catch (e: GetCredentialCancellationException) {
                postToJs("window.onNativeGoogleSignInError && window.onNativeGoogleSignInError('cancelled')")
            } catch (e: GetCredentialException) {
                val reason = if (interactive) "failed" else "no_silent_credential"
                android.util.Log.e("WebAppInterface", "nativeGoogleSignIn failed (interactive=$interactive): ${e.message}")
                postToJs("window.onNativeGoogleSignInError && window.onNativeGoogleSignInError('$reason')")
            } catch (e: Exception) {
                android.util.Log.e("WebAppInterface", "nativeGoogleSignIn unexpected error: ${e.message}")
                postToJs("window.onNativeGoogleSignInError && window.onNativeGoogleSignInError('failed')")
            }
        }
    }

    private suspend fun fetchGoogleIdToken(
        activity: android.app.Activity,
        filterByAuthorizedAccounts: Boolean,
        autoSelect: Boolean
    ): String {
        val webClientId = context.getString(R.string.google_web_client_id)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setAutoSelectEnabled(autoSelect)
            .setServerClientId(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("unexpected_credential_type")
    }

    private fun postToJs(script: String) {
        webView?.post { webView?.evaluateJavascript(script, null) }
    }

    fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        bridgeScope.cancel()
    }
}

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val ttsInterface = remember { WebAppInterface(context) }
  val webViewRef = remember { androidx.compose.runtime.mutableStateOf<WebView?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      ttsInterface.onDestroy()
    }
  }

  androidx.activity.compose.BackHandler(enabled = true) {
    val wv = webViewRef.value
    if (wv != null) {
      wv.evaluateJavascript("window.handleAndroidBackGesture ? window.handleAndroidBackGesture() : false") { result ->
        if (result == "false" || result == "null") {
          if (wv.canGoBack()) {
            wv.goBack()
          } else {
            (context as? android.app.Activity)?.finish()
          }
        }
      }
    } else {
      (context as? android.app.Activity)?.finish()
    }
  }

  // Serves the bundled web app (app/src/*/assets/dist/...) through a virtual
  // https://appassets.androidplatform.net/ origin instead of file:///
  // android_asset/. WebViewAssetLoader intercepts these requests locally —
  // no real network call happens — but the WebView sees a normal https
  // origin, which is what Firebase Auth's signInWithPopup/signInWithRedirect
  // require; they refuse to run at all from a file:// page.
  val assetLoader = remember {
    WebViewAssetLoader.Builder()
      .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
      .build()
  }

  AndroidView(
    factory = { ctx ->
      WebView(ctx).apply {
        webViewRef.value = this
        ttsInterface.webView = this
        webViewClient = object : WebViewClient() {
          override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
          ): WebResourceResponse? {
            return assetLoader.shouldInterceptRequest(request.url)
          }
        }
        addJavascriptInterface(ttsInterface, "AndroidTTS")
        
        // Custom WebChromeClient to handle popup window creations (Firebase Auth uses window.open)
        webChromeClient = object : WebChromeClient() {
          override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
          ): Boolean {
            val popupWebView = WebView(ctx).apply {
              webViewClient = WebViewClient()
              settings.javaScriptEnabled = true
              
              // Apply same cleaned UserAgent to popup window
              val defaultUA = settings.userAgentString
              val cleanedUA = defaultUA.replace("; wv", "").replace("Version/[0-9.]+".toRegex(), "")
              settings.userAgentString = cleanedUA
            }
            
            val dialog = Dialog(ctx).apply {
              setContentView(popupWebView)
              window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )
              show()
            }
            
            popupWebView.webChromeClient = object : WebChromeClient() {
              override fun onCloseWindow(window: WebView?) {
                dialog.dismiss()
              }
            }
            
            val transport = resultMsg?.obj as? WebView.WebViewTransport
            transport?.webView = popupWebView
            resultMsg?.sendToTarget()
            return true
          }
        }

        clearCache(true)
        settings.apply {
          cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
          javaScriptEnabled = true
          domStorageEnabled = true
          // allowFileAccess / allowFileAccessFromFileURLs / allowUniversal-
          // AccessFromFileURLs are gone: they only mattered for the old
          // file:// origin. WebViewAssetLoader serves everything through a
          // normal https origin now, so the WebView no longer needs (or
          // should have) broad file:// access at all.
          allowContentAccess = true

          // Support Firebase Auth Popup redirection
          setSupportMultipleWindows(true)
          javaScriptCanOpenWindowsAutomatically = true

          // Unlock sound/media autoplay so Web Audio API oscillators are never blocked
          mediaPlaybackRequiresUserGesture = false

          // Strip out WebView indicators to bypass Google's disallowed_useragent security checks
          val defaultUA = userAgentString
          val cleanedUA = defaultUA.replace("; wv", "").replace("Version/[0-9.]+".toRegex(), "")
          userAgentString = cleanedUA
        }

        val startUrl = if (context.packageName.contains("bigbook")) {
          "https://appassets.androidplatform.net/assets/dist/index.html?mode=bigbook"
        } else {
          "https://appassets.androidplatform.net/assets/dist/index.html"
        }
        loadUrl(startUrl)
      }
    },
    modifier = modifier.fillMaxSize()
  )
}

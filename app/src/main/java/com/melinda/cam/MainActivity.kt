package com.melinda.cam

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Melinda Cam v2.
 * Live view over RTSP with Media3 ExoPlayer. Setup is saved once.
 * v2 fixes: Play now shows the real error instead of a black screen,
 * it auto-tries 360p if 1080p fails, and there is a TEST CONNECTION
 * button, a SNAPSHOT button and an MJPEG live view as a backup.
 * Motion Alerts watch the snapshot picture and shout when it changes.
 * Pet Watch is motion + a saved photo — it is NOT perfect dog-only AI
 * yet, and the app says so plainly.
 * Sound: live audio is ON by default. Talk (mic) is experimental —
 * it turns the phone mic on and records your voice, but talking
 * through the camera speaker is still a work in progress in v2.
 * Remote / Away only works with Tailscale through the home computer
 * in Jamesport. Away address is the same camera IP, 10.40.1.114,
 * reached through that computer — NOT a 100.x address.
 * No ads, no Cam Plus, no paying features.
 */
class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var settingsStore: CameraSettings
    private var settings: CamSettings = CamSettings()
    private var ledOn = true
    private var nightMode = "auto"
    private var darkMode = false
    private var hugeButtons = false
    private var motionOn = false
    private var petWatchOn = false
    private var soundOn = true
    private var talking = false
    private var recorder: MediaRecorder? = null
    private var motionThread: Thread? = null
    private var lastSnapshot: ByteArray? = null
    private var lastMotionTime = 0L

    private val themeColors = listOf(
        "#FF6B9E", "#009688", "#9C27B0", "#2196F3", "#FF9800", "#4CAF50"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsStore = CameraSettings(this)
        settings = settingsStore.load()
        ledOn = settings.ledOn
        nightMode = settings.nightMode
        darkMode = settings.darkMode
        hugeButtons = settings.hugeButtons
        motionOn = settings.motionOn
        petWatchOn = settings.petWatchOn
        soundOn = settings.soundOn

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        val titleText = findViewById<TextView>(R.id.titleText)
        val playerView = findViewById<PlayerView>(R.id.playerView)
        val mjpegView = findViewById<WebView>(R.id.mjpegView)
        val petPhotoView = findViewById<ImageView>(R.id.petPhotoView)
        val playButton = findViewById<Button>(R.id.playButton)
        val statusText = findViewById<TextView>(R.id.statusText)
        val rtspText = findViewById<TextView>(R.id.rtspText)
        val hostInput = findViewById<EditText>(R.id.hostInput)
        val remoteHostInput = findViewById<EditText>(R.id.remoteHostInput)
        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val helperInput = findViewById<EditText>(R.id.helperInput)
        val helperAnswer = findViewById<TextView>(R.id.helperAnswer)
        val ledButton = findViewById<Button>(R.id.ledButton)
        val nightButton = findViewById<Button>(R.id.nightButton)
        val darkButton = findViewById<Button>(R.id.darkButton)
        val sizeButton = findViewById<Button>(R.id.sizeButton)
        val motionButton = findViewById<Button>(R.id.motionButton)
        val petButton = findViewById<Button>(R.id.petButton)
        val soundButton = findViewById<Button>(R.id.soundButton)
        val talkButton = findViewById<Button>(R.id.talkButton)

        hostInput.setText(settings.host)
        remoteHostInput.setText(settings.remoteHost)
        usernameInput.setText(settings.username)
        passwordInput.setText(settings.password)
        refreshRtspText(rtspText)
        updateLedButton(ledButton)
        updateNightButton(nightButton)
        updateDarkButton(darkButton)
        updateSizeButton(sizeButton)
        updateMotionButton(motionButton)
        updatePetButton(petButton)
        updateSoundButton(soundButton)
        updateTalkButton(talkButton)
        applyTheme(rootLayout, titleText, playButton, statusText)

        mjpegView.settings.javaScriptEnabled = true
        mjpegView.webViewClient = object : WebViewClient() {
            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler?,
                host: String?,
                realm: String?
            ) {
                handler?.proceed(settings.username, settings.password)
            }
        }

        if (settings.autoConnect && settings.activeHost.isNotBlank() && settings.password.isNotBlank()) {
            statusText.text = "Connecting to ${settings.cameraName}..."
            playRtsp(playerView, statusText, false)
        }
        if (motionOn || petWatchOn) startMotionWatch(statusText, petPhotoView)

        playButton.setOnClickListener {
            if (settings.activeHost.isBlank()) {
                statusText.text = "Type your camera IP in Setup first, then tap Save, then Play."
                Toast.makeText(this, "Type your camera IP in Setup first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            bounceTitle(titleText)
            mjpegView.visibility = View.GONE
            playRtsp(playerView, statusText, false)
        }

        findViewById<Button>(R.id.retryButton).setOnClickListener {
            if (settings.activeHost.isBlank()) {
                statusText.text = "Type your camera IP in Setup first, then tap Save, then Play."
                return@setOnClickListener
            }
            mjpegView.visibility = View.GONE
            statusText.text = "Trying again..."
            playRtsp(playerView, statusText, false)
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            player?.stop()
            mjpegView.visibility = View.GONE
            statusText.text = "Stopped."
        }

        findViewById<Button>(R.id.testButton).setOnClickListener {
            testConnection(statusText)
        }

        findViewById<Button>(R.id.snapshotButton).setOnClickListener {
            if (settings.activeHost.isBlank()) {
                Toast.makeText(this, "Type your camera IP in Setup first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            player?.stop()
            mjpegView.visibility = View.VISIBLE
            mjpegView.loadUrl(settings.snapshotUrl())
            statusText.text = "Showing a snapshot from ${settings.activeHost}. If this works, the camera is on and reachable — the problem is RTSP, not the camera."
        }

        findViewById<Button>(R.id.mjpegButton).setOnClickListener {
            if (settings.activeHost.isBlank()) {
                Toast.makeText(this, "Type your camera IP in Setup first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            player?.stop()
            mjpegView.visibility = View.VISIBLE
            mjpegView.loadUrl(settings.mjpegUrl())
            statusText.text = "Showing MJPEG live view from ${settings.activeHost}. This is the backup if RTSP will not play."
        }

        motionButton.setOnClickListener {
            motionOn = !motionOn
            settings = settings.copy(motionOn = motionOn)
            settingsStore.save(settings)
            updateMotionButton(motionButton)
            if (motionOn) {
                startMotionWatch(statusText, petPhotoView)
                Toast.makeText(this, "Motion Alerts ON — watching for movement", Toast.LENGTH_SHORT).show()
            } else {
                stopMotionWatch()
                statusText.text = "Motion Alerts OFF."
            }
        }

        petButton.setOnClickListener {
            petWatchOn = !petWatchOn
            settings = settings.copy(petWatchOn = petWatchOn)
            settingsStore.save(settings)
            updatePetButton(petButton)
            if (petWatchOn) {
                // Pet Watch needs motion watching to catch anything.
                if (!motionOn) {
                    motionOn = true
                    settings = settings.copy(motionOn = true)
                    settingsStore.save(settings)
                    updateMotionButton(motionButton)
                }
                startMotionWatch(statusText, petPhotoView)
                statusText.text = "Pet Watch ON — Where is my pet? I will take a photo when I see movement. (Motion photo, not 100% dog AI yet.)"
                Toast.makeText(this, "Pet Watch ON!", Toast.LENGTH_SHORT).show()
            } else {
                if (!motionOn) stopMotionWatch()
                statusText.text = "Pet Watch OFF."
            }
        }

        soundButton.setOnClickListener {
            soundOn = !soundOn
            settings = settings.copy(soundOn = soundOn)
            settingsStore.save(settings)
            player?.volume = if (soundOn) 1.0f else 0.0f
            updateSoundButton(soundButton)
            Toast.makeText(this, if (soundOn) "Sound ON" else "Sound OFF (muted)", Toast.LENGTH_SHORT).show()
        }

        talkButton.setOnClickListener {
            if (!talking) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
                    Toast.makeText(this, "Please allow the microphone, then tap TALK again.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                startTalking(talkButton, statusText)
            } else {
                stopTalking(talkButton, statusText)
            }
        }

        findViewById<Button>(R.id.homeButton).setOnClickListener {
            settings = settings.copy(useRemote = false)
            settingsStore.save(settings)
            statusText.text = "Home mode: Jamesport Wi-Fi. Using ${settings.host.ifBlank { "(no IP yet)" }}."
            refreshRtspText(rtspText)
            Toast.makeText(this, "Home mode", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.awayButton).setOnClickListener {
            settings = settings.copy(useRemote = true)
            settingsStore.save(settings)
            statusText.text = "Away mode: Cameron, mom's hotspot. Uses the same camera IP, 10.40.1.114, through Tailscale on your home computer in Jamesport. Not a 100.x address."
            refreshRtspText(rtspText)
            Toast.makeText(this, "Away mode — needs Tailscale", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.stream1080Button).setOnClickListener {
            settings = settings.copy(stream1080p = true)
            settingsStore.save(settings)
            refreshRtspText(rtspText)
        }

        findViewById<Button>(R.id.stream360Button).setOnClickListener {
            settings = settings.copy(stream1080p = false)
            settingsStore.save(settings)
            refreshRtspText(rtspText)
        }

        findViewById<Button>(R.id.copyRtspButton).setOnClickListener {
            copyText(settings.rtspUrl())
            Toast.makeText(this, "RTSP link copied", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            settings = settings.copy(
                host = hostInput.text.toString().trim(),
                remoteHost = remoteHostInput.text.toString().trim(),
                username = usernameInput.text.toString().trim().ifBlank { "thingino" },
                password = passwordInput.text.toString()
            )
            settingsStore.save(settings)
            refreshRtspText(rtspText)
            statusText.text = "Saved. Tap TEST CONNECTION first, then the big Play button."
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.openRecordingsButton).setOnClickListener {
            if (settings.activeHost.isBlank()) {
                Toast.makeText(this, "Type your camera IP in Setup first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(settings.webUrl()))
            startActivity(intent)
        }

        findViewById<Button>(R.id.shareButton).setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Melinda Cam — live link: ${settings.rtspUrl()}")
            }
            startActivity(Intent.createChooser(share, "Share camera link"))
        }

        findViewById<Button>(R.id.askButton).setOnClickListener {
            helperAnswer.text = AiHelper.answer(helperInput.text.toString())
        }

        findViewById<Button>(R.id.driveButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com"))
            startActivity(intent)
        }

        findViewById<Button>(R.id.megaButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mega.io"))
            startActivity(intent)
        }

        findViewById<Button>(R.id.teraboxButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.terabox.com"))
            startActivity(intent)
        }

        ledButton.setOnClickListener {
            ledOn = !ledOn
            settings = settings.copy(ledOn = ledOn)
            settingsStore.save(settings)
            updateLedButton(ledButton)
            Toast.makeText(
                this,
                if (ledOn) "Camera LED on (only works when connected)" else "Camera LED off (only works when connected)",
                Toast.LENGTH_SHORT
            ).show()
        }

        nightButton.setOnClickListener {
            nightMode = when (nightMode) {
                "auto" -> "on"
                "on" -> "off"
                else -> "auto"
            }
            settings = settings.copy(nightMode = nightMode)
            settingsStore.save(settings)
            updateNightButton(nightButton)
        }

        val themeButtons = mapOf(
            R.id.themePink to "#FF6B9E",
            R.id.themeTeal to "#009688",
            R.id.themePurple to "#9C27B0",
            R.id.themeBlue to "#2196F3",
            R.id.themeOrange to "#FF9800",
            R.id.themeGreen to "#4CAF50"
        )
        for ((buttonId, colorHex) in themeButtons) {
            findViewById<Button>(buttonId).setOnClickListener {
                setThemeColor(colorHex, rootLayout, titleText, playButton, statusText)
                Toast.makeText(this, "Theme color saved!", Toast.LENGTH_SHORT).show()
            }
        }

        sizeButton.setOnClickListener {
            hugeButtons = !hugeButtons
            settings = settings.copy(hugeButtons = hugeButtons)
            settingsStore.save(settings)
            updateSizeButton(sizeButton)
            applyTheme(rootLayout, titleText, playButton, statusText)
        }

        darkButton.setOnClickListener {
            darkMode = !darkMode
            settings = settings.copy(darkMode = darkMode)
            settingsStore.save(settings)
            updateDarkButton(darkButton)
            applyTheme(rootLayout, titleText, playButton, statusText)
        }

        findViewById<Button>(R.id.partyButton).setOnClickListener {
            val partyColor = themeColors.random()
            setThemeColor(partyColor, rootLayout, titleText, playButton, statusText)
            bounceTitle(titleText)
            Toast.makeText(this, "🎉 Party! New color!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.rokuButton).setOnClickListener {
            Toast.makeText(
                this,
                "Roku app is a separate build coming soon. This app does not control Roku yet in v2.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone allowed — tap TALK again to talk.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playRtsp(playerView: PlayerView, statusText: TextView, alreadyTriedFallback: Boolean) {
        player?.release()
        val newPlayer = ExoPlayer.Builder(this).build()
        player = newPlayer
        newPlayer.volume = if (soundOn) 1.0f else 0.0f
        playerView.player = newPlayer
        statusText.text = "Connecting ${if (settings.stream1080p) "1080p" else "360p"} from ${settings.activeHost}..."
        newPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    statusText.text = "Playing ${if (settings.stream1080p) "1080p" else "360p"} from ${settings.activeHost}."
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!alreadyTriedFallback) {
                    settings = settings.copy(stream1080p = !settings.stream1080p)
                    settingsStore.save(settings)
                    statusText.text = "That stream did not connect, trying the other one now..."
                    playRtsp(playerView, statusText, true)
                } else {
                    statusText.text = "Could not connect — check Wi-Fi / Tailscale / password. Tap TEST CONNECTION, then SHOW SNAPSHOT or VIEW WITH MJPEG below. Error: ${error.message ?: "no details"}"
                }
            }
        })
        val source = RtspMediaSource.Factory()
            .createMediaSource(MediaItem.fromUri(settings.rtspUrl()))
        newPlayer.setMediaSource(source)
        newPlayer.prepare()
        newPlayer.play()
    }

    private fun testConnection(statusText: TextView) {
        if (settings.activeHost.isBlank()) {
            statusText.text = "Type your camera IP in Setup first, then tap Save, then Test."
            return
        }
        if (settings.password.isBlank()) {
            statusText.text = "Type your camera password in Setup first, then tap Save, then Test."
            return
        }
        statusText.text = "Testing ${settings.activeHost}..."
        Thread {
            try {
                val url = URL("http://${settings.activeHost}/x/image.cgi")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                val auth = Base64.encodeToString(
                    "${settings.username}:${settings.password}".toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                conn.setRequestProperty("Authorization", "Basic $auth")
                val code = conn.responseCode
                runOnUiThread {
                    when {
                        code in 200..299 -> statusText.text = "Camera answered — it's on and reachable! Now tap Play."
                        code == 401 -> statusText.text = "Camera answered, but the password is wrong. Check the password in Setup, tap Save, and try again."
                        else -> statusText.text = "Camera answered with code $code. Try Play, or SHOW SNAPSHOT."
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Camera did not answer — check power / Wi-Fi / Tailscale / IP. (${e.javaClass.simpleName})"
                }
            }
        }.start()
    }

    // ---- Motion + Pet Watch ----
    // Polls the snapshot every 4 seconds. If the picture changes a lot
    // since the last one, that counts as motion. No ML library, just
    // comparing the picture bytes — simple, honest, and fun.
    private fun startMotionWatch(statusText: TextView, petPhotoView: ImageView) {
        stopMotionWatch()
        lastSnapshot = null
        val t = Thread {
            while (motionOn || petWatchOn) {
                try {
                    val bytes = fetchSnapshotBytes()
                    if (bytes == null) {
                        Thread.sleep(4000)
                        continue
                    }
                    val prev = lastSnapshot
                    lastSnapshot = bytes
                    if (prev != null && prev.isNotEmpty()) {
                        val changed = pictureChangeRatio(prev, bytes)
                        val now = System.currentTimeMillis()
                        if (changed > 0.15 && now - lastMotionTime > 10000) {
                            lastMotionTime = now
                            val savedFile = saveSnapshot(bytes)
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            runOnUiThread {
                                if (petWatchOn) {
                                    if (bitmap != null) {
                                        petPhotoView.setImageBitmap(bitmap)
                                        petPhotoView.visibility = View.VISIBLE
                                    }
                                    statusText.text = "I saw your pet! 🐶 (Pet Watch — motion photo, not 100% dog AI yet.) Photo saved: ${savedFile?.name ?: "on phone"}"
                                    Toast.makeText(this, "I saw your pet!", Toast.LENGTH_LONG).show()
                                } else {
                                    statusText.text = "Motion detected! Photo saved: ${savedFile?.name ?: "on phone"}"
                                    Toast.makeText(this, "Motion detected!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Camera unreachable for this poll — just try again next time.
                }
                try {
                    Thread.sleep(4000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
        motionThread = t
        t.start()
    }

    private fun stopMotionWatch() {
        motionThread?.interrupt()
        motionThread = null
    }

    private fun fetchSnapshotBytes(): ByteArray? {
        if (settings.activeHost.isBlank() || settings.password.isBlank()) return null
        val url = URL("http://${settings.activeHost}/x/image.cgi")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "GET"
        val auth = Base64.encodeToString(
            "${settings.username}:${settings.password}".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        conn.setRequestProperty("Authorization", "Basic $auth")
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            return null
        }
        val bytes = conn.inputStream.readBytes()
        conn.disconnect()
        return bytes
    }

    private fun pictureChangeRatio(a: ByteArray, b: ByteArray): Double {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0.0
        var diff = 0
        var i = 0
        while (i < n) {
            if (a[i] != b[i]) diff++
            i++
        }
        return diff.toDouble() / n.toDouble()
    }

    private fun saveSnapshot(bytes: ByteArray): File? {
        return try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val file = File(dir, "melinda-cam-motion-${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { it.write(bytes) }
            file
        } catch (e: Exception) {
            null
        }
    }

    // ---- Sound + Talk ----
    private fun startTalking(talkButton: Button, statusText: TextView) {
        try {
            val rec = MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            rec.setOutputFile(File(cacheDir, "melinda-talk.3gp").absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            talking = true
            updateTalkButton(talkButton)
            statusText.text = "Talking — your voice is on (mic recording). Talking through the camera speaker is experimental in v2 — a work in progress, not promised yet."
        } catch (e: Exception) {
            talking = false
            updateTalkButton(talkButton)
            statusText.text = "Microphone would not start (${e.javaClass.simpleName}). Try again."
        }
    }

    private fun stopTalking(talkButton: Button, statusText: TextView) {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            // Already stopped — ignore.
        }
        recorder = null
        talking = false
        updateTalkButton(talkButton)
        statusText.text = "Talking off. Your voice clip was saved on the phone."
    }

    private fun setThemeColor(
        colorHex: String,
        rootLayout: LinearLayout,
        titleText: TextView,
        playButton: Button,
        statusText: TextView
    ) {
        settings = settings.copy(themeColor = colorHex)
        settingsStore.save(settings)
        applyTheme(rootLayout, titleText, playButton, statusText)
    }

    private fun applyTheme(
        rootLayout: LinearLayout,
        titleText: TextView,
        playButton: Button,
        statusText: TextView
    ) {
        val color = try {
            Color.parseColor(settings.themeColor)
        } catch (e: Exception) {
            Color.parseColor("#FF6B9E")
        }
        playButton.setBackgroundColor(color)
        titleText.setTextColor(color)
        val playHeight = if (hugeButtons) dpToPx(96) else dpToPx(72)
        playButton.layoutParams.height = playHeight
        playButton.textSize = if (hugeButtons) 28f else 24f
        playButton.requestLayout()
        if (darkMode) {
            rootLayout.setBackgroundColor(Color.parseColor("#222222"))
            statusText.setTextColor(Color.WHITE)
        } else {
            rootLayout.setBackgroundColor(Color.WHITE)
            statusText.setTextColor(Color.BLACK)
        }
    }

    private fun updateDarkButton(button: Button) {
        button.text = if (darkMode) "DARK MODE: ON — TAP FOR LIGHT" else "LIGHT MODE: ON — TAP FOR DARK"
    }

    private fun updateSizeButton(button: Button) {
        button.text = if (hugeButtons) "BUTTON SIZE: HUGE — TAP FOR BIG" else "BUTTON SIZE: BIG — TAP FOR HUGE"
    }

    private fun updateMotionButton(button: Button) {
        button.text = if (motionOn) "MOTION ALERTS: ON — TAP TO TURN OFF" else "MOTION ALERTS: OFF — TAP TO TURN ON"
    }

    private fun updatePetButton(button: Button) {
        button.text = if (petWatchOn) "PET WATCH: ON — TAP TO TURN OFF" else "PET WATCH: OFF — TAP TO TURN ON"
    }

    private fun updateSoundButton(button: Button) {
        button.text = if (soundOn) "SOUND: ON — TAP TO MUTE" else "SOUND: OFF — TAP TO UNMUTE"
    }

    private fun updateTalkButton(button: Button) {
        button.text = if (talking) "🎤 TALKING — TAP TO STOP" else "🎤 TALK — MICROPHONE"
    }

    private fun bounceTitle(titleText: TextView) {
        titleText.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200)
            .withEndAction {
                titleText.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            }.start()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun refreshRtspText(rtspText: TextView) {
        rtspText.text = if (settings.activeHost.isBlank()) {
            "RTSP is for this Android app and VLC. Browsers cannot play RTSP, so the web view uses MJPEG."
        } else {
            "RTSP now: ${settings.rtspUrl()}\nMJPEG for web view: ${settings.mjpegUrl()}"
        }
    }

    private fun updateLedButton(button: Button) {
        button.text = if (ledOn) "CAMERA LED: ON — TAP TO TURN OFF" else "CAMERA LED: OFF — TAP TO TURN ON"
    }

    private fun updateNightButton(button: Button) {
        button.text = "NIGHT MODE: ${nightMode.uppercase()}"
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Melinda Cam", text))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMotionWatch()
        try {
            recorder?.release()
        } catch (e: Exception) {
        }
        recorder = null
        player?.release()
        player = null
    }
}

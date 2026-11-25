package com.melinda.cam

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView

/**
 * Melinda Cam v1.
 * Live view over RTSP with Media3 ExoPlayer. Setup is saved once.
 * Remote / Away only works with Tailscale through the home computer
 * in Jamesport — see README. No ads, no Cam Plus, no paying features.
 */
class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var settingsStore: CameraSettings
    private var settings: CamSettings = CamSettings()
    private var ledOn = true
    private var nightMode = "auto"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsStore = CameraSettings(this)
        settings = settingsStore.load()
        ledOn = settings.ledOn
        nightMode = settings.nightMode

        val playerView = findViewById<PlayerView>(R.id.playerView)
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

        hostInput.setText(settings.host)
        remoteHostInput.setText(settings.remoteHost)
        usernameInput.setText(settings.username)
        passwordInput.setText(settings.password)
        refreshRtspText(rtspText)
        updateLedButton(ledButton)
        updateNightButton(nightButton)

        // Auto-connect: camera IP is pre-filled (10.40.1.114). Once the
        // password has been saved once, just open the app and it plays.
        if (settings.autoConnect && settings.activeHost.isNotBlank() && settings.password.isNotBlank()) {
            statusText.text = "Connecting to ${settings.cameraName}..."
            playRtsp(playerView, statusText)
        }

        findViewById<Button>(R.id.playButton).setOnClickListener {
            if (settings.activeHost.isBlank()) {
                statusText.text = "Type your camera IP in Setup first, then tap Save, then Play."
                Toast.makeText(this, "Type your camera IP in Setup first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            playRtsp(playerView, statusText)
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            player?.stop()
            statusText.text = "Stopped."
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
            statusText.text = "Away mode: Cameron, mom's hotspot. This only works with Tailscale through your home computer in Jamesport. Put the camera's Tailscale address in Setup first."
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
            statusText.text = "Saved. Now tap the big Play button."
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.openRecordingsButton).setOnClickListener {
            if (settings.activeHost.isBlank()) {
                Toast.makeText(this, "Type your camera IP in Setup first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            // Thingino serves recordings from its web interface.
            // v1 opens that page so she can tap a recording to download / share.
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

        findViewById<Button>(R.id.rokuButton).setOnClickListener {
            Toast.makeText(
                this,
                "Roku app is a separate build coming soon. This app does not control Roku yet in v1.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun playRtsp(playerView: PlayerView, statusText: TextView) {
        player?.release()
        val newPlayer = ExoPlayer.Builder(this).build()
        player = newPlayer
        playerView.player = newPlayer
        val source = RtspMediaSource.Factory()
            .createMediaSource(MediaItem.fromUri(settings.rtspUrl()))
        newPlayer.setMediaSource(source)
        newPlayer.prepare()
        newPlayer.play()
        statusText.text = "Playing ${if (settings.stream1080p) "1080p" else "360p"} from ${settings.activeHost}."
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
        player?.release()
        player = null
    }
}

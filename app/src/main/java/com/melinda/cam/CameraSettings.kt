package com.melinda.cam

import android.content.Context
import android.content.SharedPreferences

/**
 * Save the camera settings on the phone only.
 * No password is in the code. The user types it in Setup.
 */
data class CamSettings(
    val cameraName: String = "thingino-wyze-cam3-3610",
    val host: String = "10.40.1.114",
    val remoteHost: String = "",
    val username: String = "thingino",
    val password: String = "",
    // v2.1: TWO passwords. The camera/root password (above) is for HTTP
    // (Test Connection, Snapshot, MJPEG) with user "root". The stream
    // password (below) is a SEPARATE Thingino streamer password for RTSP
    // live video, user "thingino", default "thingino" (the public
    // Thingino default — NOT Melinda's private root password).
    val streamPassword: String = "thingino",
    val useRemote: Boolean = false,
    val autoConnect: Boolean = true,
    val stream1080p: Boolean = true,
    val themeColor: String = "#FF6B9E",
    val bigButtons: Boolean = true,
    val ledOn: Boolean = true,
    val nightMode: String = "auto",
    val irOn: Boolean = true,
    val darkMode: Boolean = false,
    val hugeButtons: Boolean = false,
    val motionOn: Boolean = false,
    val petWatchOn: Boolean = false,
    val soundOn: Boolean = true
) {
    /** The address to use right now: home, or Tailscale remote when Away. */
    val activeHost: String
        get() = if (useRemote && remoteHost.isNotBlank()) remoteHost.trim() else host.trim()

    /** RTSP link for the Android app and VLC. Browsers cannot play RTSP.
     *  v2.1: RTSP uses the STREAM password (thingino by default), NOT
     *  the camera/root password. */
    fun rtspUrl(): String {
        val channel = if (stream1080p) "ch0" else "ch1"
        return "rtsp://$username:$streamPassword@${activeHost}/$channel"
    }

    /** HTTP user for Test Connection / Snapshot / MJPEG: "root". */
    fun httpUser(): String = "root"

    /** HTTP password is the camera/root password Melinda set in setup. */
    fun httpPassword(): String = password

    /** MJPEG link for a web view. Browsers can show MJPEG. */
    fun mjpegUrl(): String = "http://${activeHost}/x/mjpeg.cgi"

    /** Still image link for a web view. */
    fun snapshotUrl(): String = "http://${activeHost}/x/image.cgi"

    /** Thingino web page, for recordings on the SD card. */
    fun webUrl(): String = "http://${activeHost}/"
}

class CameraSettings(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("melinda_cam", Context.MODE_PRIVATE)

    fun load(): CamSettings = CamSettings(
        cameraName = prefs.getString("camera_name", "thingino-wyze-cam3-3610") ?: "thingino-wyze-cam3-3610",
        host = prefs.getString("host", "10.40.1.114") ?: "10.40.1.114",
        remoteHost = prefs.getString("remote_host", "") ?: "",
        username = prefs.getString("username", "thingino") ?: "thingino",
        password = prefs.getString("password", "") ?: "",
        streamPassword = prefs.getString("stream_password", "thingino") ?: "thingino",
        useRemote = prefs.getBoolean("use_remote", false),
        autoConnect = prefs.getBoolean("auto_connect", true),
        stream1080p = prefs.getBoolean("stream_1080p", true),
        themeColor = prefs.getString("theme_color", "#FF6B9E") ?: "#FF6B9E",
        bigButtons = prefs.getBoolean("big_buttons", true),
        ledOn = prefs.getBoolean("led_on", true),
        nightMode = prefs.getString("night_mode", "auto") ?: "auto",
        irOn = prefs.getBoolean("ir_on", true),
        darkMode = prefs.getBoolean("dark_mode", false),
        hugeButtons = prefs.getBoolean("huge_buttons", false),
        motionOn = prefs.getBoolean("motion_on", false),
        petWatchOn = prefs.getBoolean("pet_watch_on", false),
        soundOn = prefs.getBoolean("sound_on", true)
    )

    fun save(s: CamSettings) {
        prefs.edit()
            .putString("camera_name", s.cameraName)
            .putString("host", s.host)
            .putString("remote_host", s.remoteHost)
            .putString("username", s.username)
            .putString("password", s.password)
            .putString("stream_password", s.streamPassword)
            .putBoolean("use_remote", s.useRemote)
            .putBoolean("auto_connect", s.autoConnect)
            .putBoolean("stream_1080p", s.stream1080p)
            .putString("theme_color", s.themeColor)
            .putBoolean("big_buttons", s.bigButtons)
            .putBoolean("led_on", s.ledOn)
            .putString("night_mode", s.nightMode)
            .putBoolean("ir_on", s.irOn)
            .putBoolean("dark_mode", s.darkMode)
            .putBoolean("huge_buttons", s.hugeButtons)
            .putBoolean("motion_on", s.motionOn)
            .putBoolean("pet_watch_on", s.petWatchOn)
            .putBoolean("sound_on", s.soundOn)
            .apply()
    }
}

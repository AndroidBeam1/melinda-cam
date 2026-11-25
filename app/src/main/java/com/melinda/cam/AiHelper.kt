package com.melinda.cam

/**
 * Small built-in helper. Plain answers, no cloud, not a real person.
 * It only answers questions about this camera app.
 */
object AiHelper {
    fun answer(question: String): String {
        val q = question.lowercase()
        return when {
            "motion" in q -> "Motion: in Thingino, turn on Motion in Settings, pick how sensitive it is from 1 to 8, and pick Save to SD card if you want it to record when it sees movement. It only works when the camera is connected."
            "night" in q || "ir" in q || "infrared" in q -> "Night vision: Auto means the camera turns on the IR lights by itself when it gets dark. On means night mode all the time. Off means color all the time. IR lights help you see in the dark, but the picture turns black and white."
            "led" in q || "light" in q -> "LED: the little light on the camera. On means it lights up. Off means it stays dark. This only works when the camera is connected."
            "rtsp" in q -> "RTSP is the video link the Android app and VLC use. 1080p is rtsp://thingino:thingino@your-camera-ip/ch0 and 360p is /ch1. Browsers cannot play RTSP, so the web view uses MJPEG instead."
            "record" in q || "sd card" in q || "recording" in q -> "Recordings live on the SD card in the camera. Open Recordings, tap a recording to see it, tap Download to save it to your phone, tap Share to send it to your device. If there is no camera connected yet, it will say so and tell you how to connect."
            "away" in q || "cameron" in q || "remote" in q || "far" in q -> "Away / remote: Thingino is local-only, so it does not work from another Wi-Fi by itself. You need Tailscale, a free VPN, through your home computer in Jamesport. That computer stays on, runs Tailscale, and is the bridge to the camera. Your phone in Cameron runs Tailscale too and talks through that computer. Put the camera's Tailscale address in the Away field in Setup."
            "tailscale" in q -> "Tailscale checklist: 1. Install Tailscale on your home computer in Jamesport. 2. Sign in to the same account. 3. Leave the computer on and on home Wi-Fi. 4. Install Tailscale on this phone. 5. Put the camera's Tailscale address in the app once, in the Away field."
            "roku" in q -> "Roku is a separate build coming soon. This app does not control your Roku yet in v1. The Roku tab in the app tells you that, and shows your MelindaHome Roku work."
            "share" in q -> "Share: tap Share on Live or on a recording. It opens your phone's share sheet so you can send the link or the recording to your device or to someone else."
            "password" in q || "ip" in q || "setup" in q -> "Setup once: go to Setup, type the camera IP or hostname, your username (usually thingino), and your password, then tap Save. You only have to do it once, the phone remembers it."
            "play" in q || "live" in q -> "Live view: go to Live, tap the big Play button. If it says it cannot connect, check the camera is on, you are on the right Wi-Fi, and the IP in Setup is right."
            else -> "I can help with motion, night vision, IR, LED, RTSP, recording, Away / Tailscale, sharing, Roku, and setup. Try asking about one of those in plain words."
        }
    }
}

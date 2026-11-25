# Melinda Cam — Android app for Thingino Wyze Cam v3

A real Android app for Melinda's Wyze Cam v3 running Thingino firmware.
No Wyze app, no cloud, no Cam Plus, no ads, no paying features. Free.

## Get the APK

Every push to `main` builds the APK with GitHub Actions and attaches it
to a release. Download the newest release here:

https://github.com/AndroidBeam1/melinda-cam/releases

Tap the `app-debug.apk` file, then open it on your phone to install.
Your phone may ask to allow installing from your browser — tap Allow.

## Already set up in the app

- Camera name: `thingino-wyze-cam3-3610`
- Camera IP at home: `10.40.1.114` (already filled in)
- Username: `thingino`
- Auto-connect is on: once you type your camera password in Setup and
  tap Save, just open the app and it plays by itself.

No password is in this repo or in the APK. You type your own camera
password in the app's Setup screen, and it stays on your phone only.

## What it does (v1)

- Setup once: camera IP / hostname, username (default `thingino`),
  password. Saved on the phone only, in SharedPreferences.
  No password is in this code. You type your own password in the app.
- Big Play button for live view.
- Live view over RTSP, using Media3 ExoPlayer:
  - 1080p: `rtsp://thingino:thingino@<camera-ip>/ch0`
  - 360p: `rtsp://thingino:thingino@<camera-ip>/ch1`
  - Replace `<camera-ip>` with your camera's address, and `thingino`
    with your own username/password if you changed them.
  - The app builds the RTSP URL from the settings you saved.
  - Tap 1080p / 360p to switch. Tap Copy to copy the RTSP link.
- Home / Away mode:
  - Home = Jamesport, on the same Wi-Fi as the camera.
    Use the camera's home address, for example `192.168.1.50`
    (use your camera's real address, not this example).
  - Away = Cameron, on mom's hotspot, different Wi-Fi.
    Thingino is local-only. Remote viewing does NOT work by itself
    from another Wi-Fi.
    Remote plan: Melinda keeps it running on her computer inside
    a Vercel app page. Tailscale goes through her computer at home
    in Jamesport. That computer stays on, runs Tailscale, and is
    the bridge to the camera. Her phone in Cameron on mom's hotspot
    runs Tailscale too, opens the Vercel app page, and talks to the
    camera through that home computer. No separate Raspberry Pi
    or router is assumed — her computer is the relay.
    Tailscale checklist:
    1. Install Tailscale on the home computer in Jamesport.
    2. Sign in to the same Tailscale account on the computer.
    3. Leave the computer on and on home Wi-Fi.
    4. Install Tailscale on the phone.
    5. Put the camera's Tailscale address in the app once,
       in the "Away / remote address" field.
    6. Then Away mode uses that Tailscale address.
  - Do not type your home address in the app. The app never
    shares your home address.
- Recordings: the app has a Recordings screen that explains how to
  open the camera's SD card recordings from Thingino.
  Thingino serves recordings from its web interface.
  In v1, tap "Open recordings in browser" to open
  `http://<camera-ip>/` in a browser, then tap a recording to
  download / share it from there. A built-in list will come after
  the camera is connected and we can see its exact recording paths.
  No fake recordings are included.
- Share: Share button copies / shares the camera's RTSP link and
  a recording link using Android's share sheet.
- AI helper: a small built-in helper in the app with short plain
  answers about motion, night vision, IR, LED, RTSP and recording.
  It is a helper in the app, not a real person, and it needs no cloud.
- Customization: theme color picker, big buttons on/off, light colors,
  camera LED on/off, IR / night mode. Those camera controls only work
  when the app is connected to the camera.
- Roku: a Roku tab in the app that says the Roku app is a separate
  build coming soon. It does NOT claim Roku control works in v1.

## Build it (on your computer)

You need:
- A computer with Android Studio installed (free, from Google).
- Android Studio Hedgehog or newer.
- An Android phone to test on, or the Android emulator.

Steps:
1. Open Android Studio.
2. Open this folder: `melinda-cam-android`
3. Let Android Studio download the Gradle files. It needs internet
   the first time.
4. Connect your Samsung Galaxy S23 FE by USB, turn on Developer
   options > USB debugging on the phone, and tap Allow on the phone.
   Or run it in the emulator.
5. Press Run (the green play button) in Android Studio.
   Android Studio builds the APK and installs it on the phone.
6. Open Melinda Cam on the phone.
7. Go to Setup, type the camera IP, username and password, tap Save.
8. Go to Live, tap the big Play button.

To make an APK file to keep:
- In Android Studio: Build > Build App Bundle(s) / APK(s) >
  Build APK(s). The APK is in `app/build/outputs/apk/debug/`.

## Files

- `settings.gradle.kts` — project name, app module.
- `build.gradle.kts` — project plugins.
- `app/build.gradle.kts` — app, Media3 ExoPlayer RTSP, Kotlin.
- `app/src/main/AndroidManifest.xml` — app name, internet permission,
  cleartext HTTP allowed for local camera (http:// on your home Wi-Fi).
- `app/src/main/java/com/melinda/cam/MainActivity.kt` — all screens:
  Live, Recordings, AI Helper, Customize, Roku, Setup.
- `app/src/main/java/com/melinda/cam/CameraSettings.kt` — save/load settings.
- `app/src/main/java/com/melinda/cam/AiHelper.kt` — plain answers, no cloud.
- `app/src/main/res/layout/activity_main.xml` — big buttons layout.
- `app/src/main/res/values/themes.xml` — app theme.
- `gradle/wrapper/gradle-wrapper.properties` — Gradle version.

## Thingino streams (for reference)

Web view (browsers cannot play RTSP directly):
- MJPEG: `http://<camera-ip>/x/mjpeg.cgi`
- Still image: `http://<camera-ip>/x/image.cgi`

Android app / VLC:
- 1080p RTSP: `rtsp://thingino:thingino@<camera-ip>/ch0`
- 360p RTSP: `rtsp://thingino:thingino@<camera-ip>/ch1`
- ONVIF: username `thingino`, port `80` (use your own password)

Change `thingino:thingino` to your own username:password if you
changed them in Thingino.

## No invented credentials

No real camera IP, password, or account is in this project.
The examples above use placeholders. Type your real camera IP and
password in the app's Setup screen on your phone. Do not share them.

# How Far Is Graceland?

**[Download Latest Android Debug APK (`graceland-debug.apk`)](https://github.com/randaladams/graceland2/releases/latest/download/graceland-debug.apk)**

An Android app (Kotlin + Jetpack Compose) that displays full-screen rotating photos of Graceland based on the time of day in Memphis, shows live clocks for Memphis (Central Time) and your local time zone, and calculates the real-time distance and live compass direction to Graceland (`35.046000, -90.022904`).

## Features

- **Full-Screen Rotating Graceland Backgrounds**: Automatically selects photos for **Morning** (`05:00–10:59`), **Midday** (`11:00–16:59`), **Dusk** (`17:00–19:59`), and **Night** (`20:00–04:59`) in Memphis time (`America/Chicago`) and crossfades between images in that folder every 12 seconds.
- **"How far is Graceland?" Center Button**: Requests GPS/location permission, computes the exact distance and great-circle bearing to Graceland (`35.046000, -90.022904`), and displays a live compass arrow that rotates smoothly as you turn your phone.
- **Two-Line Time Box**: Line 1 shows **Elvis Time:** (date and Memphis Central Time) and Line 2 shows **Local Time:** (your location time), formatted using your phone's native date and 12/24-hour settings. Tap the time box to toggle distance units between **Imperial** (`mi` / `ft`) and **Metric** (`km` / `m`).
- **Eye Peek Button**: Press and hold the eye button below the time box on the right side to hide all overlays and admire the full-screen Graceland background; release to restore the UI.

## Adding Your Own Graceland Photos

Your Graceland photos are bundled in these time-of-day folders:

- `app/src/main/assets/backgrounds/morning/`
- `app/src/main/assets/backgrounds/midday/`
- `app/src/main/assets/backgrounds/dusk/`
- `app/src/main/assets/backgrounds/night/`

To add more photos, upload `.jpg`, `.jpeg`, `.png`, or `.webp` files into any of those folders (or place files named with `morning`, `midday`, `dusk`, or `night` directly in `app/src/main/assets/backgrounds/`). The app automatically discovers and rotates through all matching images based on the current time in Memphis (`America/Chicago`).

## Building & Downloading the APK on GitHub

Every push to `main` (or manual run from the **Actions** tab) compiles the Android app in GitHub Actions and updates the direct APK download:

- **Direct APK download**: [graceland-debug.apk](https://github.com/randaladams/graceland2/releases/latest/download/graceland-debug.apk)
- **Releases page**: https://github.com/randaladams/graceland2/releases
- **Workflow artifacts**: Also available as `graceland-debug-apk` under the **Actions** tab.

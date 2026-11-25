# How Far Is Graceland?

An Android app (Kotlin + Jetpack Compose) that displays full-screen rotating photos of Graceland based on the time of day in Memphis, shows live clocks for Memphis (Central Time) and your local time zone, and calculates the real-time distance and live compass direction to Graceland (`35.046000, -90.022904`).

## Features

- **Full-Screen Rotating Graceland Backgrounds**: Automatically selects photos for **Morning** (`05:00–10:59`), **Midday** (`11:00–16:59`), **Dusk** (`17:00–19:59`), and **Night** (`20:00–04:59`) in Memphis time (`America/Chicago`) and crossfades between images in that folder every 12 seconds.
- **"How far is Graceland?" Center Button**: Requests GPS/location permission, computes the exact distance and great-circle bearing to Graceland (`35.046000, -90.022904`), and displays a live compass arrow that rotates smoothly as you turn your phone.
- **Dual Live Clocks**: Displays Memphis, TN (`America/Chicago`) alongside your local time zone (and resolved city/region when location is active).
- **Imperial / Metric Toggle**: Switch between **Imperial** (`mi` / `ft` and 12-hour `AM/PM` time) and **Metric** (`km` / `m` and 24-hour time). Your choice is saved automatically.

## Adding Your Own Graceland Photos

Starter placeholder images are included so the app builds and runs immediately. To use your own photos, upload `.jpg`, `.jpeg`, `.png`, or `.webp` files into these folders:

- `app/src/main/assets/backgrounds/morning/`
- `app/src/main/assets/backgrounds/midday/`
- `app/src/main/assets/backgrounds/dusk/`
- `app/src/main/assets/backgrounds/night/`

You can delete the starter `*_1.png` / `*_2.png` files once your photos are uploaded. Any filename works — the app automatically discovers and rotates through all images in each folder.

## Building & Downloading the APK on GitHub

Every push to `main` (or manual run from the **Actions** tab) compiles the Android app in GitHub Actions:

1. Open the **Actions** tab in your GitHub repository.
2. Click the latest **Build Android APK** workflow run.
3. Download the **`graceland-debug-apk`** artifact (`app-debug.apk`) and install it on your Android device.

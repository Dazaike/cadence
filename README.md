# Cadence

**A simple way to see and understand your health data.**

Cadence is an Android app that brings your health information together in one place. Check your activity, heart rate, sleep, and more with easy-to-read charts and a dashboard you can personalize.

It works with **Health Connect** and can also read from **Samsung Health**.

## What can Cadence do?

- **Track your activity:** See your steps, exercise, and progress toward your goals.
- **Understand your sleep:** Review how long you slept and your sleep patterns.
- **Check your heart health:** View heart rate trends and other available heart measurements.
- **See your progress:** Compare days, weeks, and months to spot changes over time.
- **Personalize your dashboard:** Choose which health stats you want to see first.
- **Get helpful insights:** Discover patterns in your activity, sleep, and overall routine.
- **Use widgets:** Check important stats from your phone's home screen.
- **Share or export your data:** Save your information or share it with an AI assistant.

Some features depend on which health information your phone or connected apps have recorded.

## Your data, your choice

Cadence is designed with privacy in mind.

- No ads or trackers.
- No account required for viewing health data on your phone.
- Your health data stays on your device unless you choose to turn on cloud sync.
- Cadence only reads your health data. It doesn't change the records in your health apps.

### Optional cloud sync

Want to check your health stats from another device? Sign in and turn on cloud sync to use the **Cadence web dashboard**.

Cloud sync can also let supported AI assistants, including ChatGPT and Claude, access your synced health information when you set up that connection.

**Cloud sync is optional.** If you enable it, your health information is uploaded to your Cadence cloud account. Signing out stops future syncing, but information already uploaded remains in your account until you delete it from the dashboard.

## Getting started

1. Install Cadence on your Android phone.
2. Allow access to the health information you'd like to see.
3. Open the app and explore your dashboard.

For developers who want to build the Android app themselves:

```bash
git clone https://github.com/Dazaike/cadence
cd cadence
./gradlew :app:assembleDebug
```

The APK will be in `app/build/outputs/apk/debug/`. Building requires JDK 17 and the Android SDK. More details are in [the build guide](docs/REPRODUCIBLE.md).

## Credits

Cadence began as a fork of [Heartwood](https://github.com/GuyOnWifi/heartwood).

Licensed under [GPL-3.0-or-later](LICENSE). Original copyright © Eason Huang.

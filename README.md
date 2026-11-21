# Pocket GBA

A small, offline-capable Game Boy Advance emulator for Android, built for Arthur Henrique Tostes Batista Aranha.

## Games on the wish list

The library initially has five **empty slots**:

1. Super Mario Advance 4: Super Mario Bros. 3 (GBA)
2. Super Mario Advance 2: Super Mario World (GBA)
3. Hulk / Hook (title and platform need confirmation)
4. Bomberman 2 (precise GBA title needs confirmation)
5. Bomberman 3 (precise GBA title needs confirmation)

**No commercial games, ROMs, or Nintendo BIOS files are distributed with this project or APK.** Import your own legally obtained `.gba` files using the on-device file picker. You can also add unlimited other GBA games later without rebuilding the APK. The app copies imported files into private app storage and never uploads them.

The emulator engine is [EmulatorJS](https://github.com/EmulatorJS/EmulatorJS) with its [mGBA core](https://github.com/EmulatorJS/mgba). The Android wrapper and library UI are developed in this repository. EmulatorJS is licensed under GPL-3.0; its source, license, and attributions must be preserved when redistributing this app. The build bundles these open-source components into the APK at build time so gameplay does not need an internet connection.

## Download and install

1. Open the repository's [Actions](../../actions) tab and select **Build Android APK**.
2. Open the most recent successful run and download **Pocket-GBA-debug-APK**, or use the GitHub [Releases](../../releases) page if a release has been published.
3. Install `app-debug.apk` on Android 6.0 or later, allowing installation from that source if prompted.
4. Tap **Import ROM** beside a game, or **Add a GBA game**, and select a `.gba` file stored on the phone.
5. Tap **Play**; the on-screen controls and emulator menu come from EmulatorJS.

Game saves/states are managed by the emulator and stored in the app's WebView storage. Removing the app or clearing its storage may erase them. This is an early build and has **not yet been verified on a physical device**; please report issues with model and Android version.

## Developer build

Requires JDK 17, Gradle 8.10.2, Android SDK Platform 35, and Node.js 22.

```bash
npm install --omit=optional --ignore-scripts
node scripts/bundle-core.mjs
gradle --no-daemon :app:assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs the same steps on every push to `main` and also supports manual runs.

## Scope

- Single-system GBA emulator (not SNES or Game Boy/Color)
- Default English UI, optional Brazilian Portuguese
- Local ROM import; no ROM search, store, or downloader
- Free/open-source emulation engine, no ads

The original repository name `settlement-game-cards` is retained for now and can be renamed independently of the Android package.

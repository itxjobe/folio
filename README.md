# Bifold

An iOS-style Android launcher, tuned for the Samsung Galaxy Z Fold 8: a 4-column cover screen and a 5-column inner screen that move with you as you fold and unfold.

## Credit where it's due

**Bifold is a fork of [Folio by McCal-Codes](https://github.com/McCal-Codes/folio).** The core launcher, its Home screen, Dynamic Island, panels, widgets engine, backups and almost everything under the hood are their work. I'm building on top of it for my own phone, and I'm grateful they made it open source.

If you like Bifold, please go [star and support the original Folio](https://github.com/McCal-Codes/folio). Bug reports that aren't specific to Bifold's changes belong there too.

Bifold is released under the same MIT License as Folio (see [LICENSE](LICENSE)). The original copyright and license notices, and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), are kept as they are and apply to this fork.

## Highlights

- Installs as its own app, `com.joshreid.bifold`, so it can sit beside Folio.
- iPhone Fold-style Home by default: 4 columns on the cover screen, 5 on the inner screen, with icons that spring between layouts when you fold.
- Redesigned Control Center in an iOS 26 look, with controls you can reorder, add and remove.
- iOS-style widgets for Weather, Calendar, Batteries and Now Playing in small, medium and large sizes.
- iOS status icons (signal bars, Wi-Fi fan, battery, 5G/LTE label off Wi-Fi) and a green charging battery with a lightning bolt.
- Optional Shizuku support (1.2.0) for real Control Center toggles and hiding Samsung's bars.

## What's different from Folio

**Fold 8 layout**
- iPhone Fold look as the default Home, with a one-time upgrade for existing installs.
- Inner screen uses 5 columns as a reflow of your saved layout; the cover keeps 4.
- Icons spring between 4- and 5-column cells and Home cross-fades on fold and unfold.
- Inner upright layout gets its own app rows from the current window size.
- iPadOS-style recent apps in the inner screen's dock.

**Control Center**
- iOS 26-style redesign: light glass, round toggles, a connectivity mini row, pill sliders, Focus, colorful shortcuts and a status column, sized to fill the screen.
- Reorder, add and remove controls in Settings › Notifications & Control Center › Control Center Controls.

**Widgets**
- Native glass Weather, Batteries and Now Playing widgets.
- iOS small/medium/large families: Weather (hourly, 5-day), Calendar (week), Batteries (rings, Bluetooth), Now Playing (album art, previous track).
- Location widget.
- Weather retries failed fetches, keeps today's data, and asks for a fresh location on a new phone. Weather shows °F or °C.
- Widgets stay readable over light wallpapers.

**Status bar and camera cutout**
- iOS icons by default: crisp signal bars, Wi-Fi fan, battery, and network type (5G/LTE) when off Wi-Fi.
- Green fill and lightning bolt while charging, whatever battery style you pick.
- Dynamic Island wraps the actual camera lens instead of Samsung's tall cutout area, and island content clears the cutout.
- Status rail taps work like iOS.

**Motion and haptics**
- iOS rubber-band overscroll on Home pages, and Home springs back when an app closes.
- A light haptic tick when a Home page swipe settles.
- System edge gestures kept off the launcher's own gesture zones.

**Dynamic Island**
- Ride-share/delivery and Android 16 Live Updates show as live activities.

**Settings and cleanup**
- Settings regrouped by look, Home, panels, interaction and device, with plainer names and a per-screen Grid card (columns, app names, dock recents).
- Removed the original developer's update checks, Market, supporter codes, in-app roadmap, testers' list and bug-report inbox. Share Diagnostics sends a report wherever you choose.
- Setup asks for notification access and approximate location, both skippable.
- Appearance follows the system theme by default; icon packs per theme with a Liquid Glass default.

**Backups and moving from Folio**
- Backups save as `bifold-layout.json` and carry every setting, not just the layout.
- Folio layout backups and theme files import unchanged, and restore reads Folio's saved preferences, so a Folio setup moves over whole.

**Stability fixes**
- Fixes for stuck Notification/Control Center panels, a launch crash, duplicate Home tasks, the Charging focus trigger, Big Clock font/color options, the dock grabber, the Notification Shade shortcut and Bottom of Home choices.
- A crash report viewer with per-report sharing.

## Shizuku extras

[Shizuku](https://shizuku.rikka.app/) gives apps adb-level access without root. Bifold works fine without it; when Shizuku is running, extra features switch on by themselves.

- **Settings › Advanced › Shizuku** shows whether Shizuku is installed, running and allowed, with a button to allow it.
- Control Center's Wi-Fi, Bluetooth, mobile data, airplane mode and hotspot buttons switch in place. Rotation lock and Do Not Disturb work without their own permissions. Without Shizuku, the buttons open system settings as before.
- Hide Samsung's status bar and navigation on Home (no Shizuku needed), or hide the gesture pill everywhere (Shizuku). Android 16 doesn't let any app hide the status bar inside other apps.

**Start on boot.** The official Shizuku hasn't been updated in a while, so I recommend a maintained fork such as [Shizuku Next](https://github.com/rushiranpise/Shizuku-Next), which can restart itself over wireless debugging after a reboot. Grant it this once from a computer:

```sh
adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS
```

After a reboot it starts once the phone is on Wi-Fi.

## Install

**From releases:** grab the APK from this repo's Releases page and install it. It installs beside Folio as `com.joshreid.bifold`.

**Build from source** (JDK 17 and the Android SDK):

```sh
./gradlew :app:assembleRelease
```

Release signing is read from environment variables, so no keys live in the repo. Set all four, or none (then the release build is unsigned/debug-signed):

```sh
export FOLIO_RELEASE_STORE_FILE=/path/outside/the/repo/release.jks
export FOLIO_RELEASE_STORE_PASSWORD=...
export FOLIO_RELEASE_KEY_ALIAS=...
export FOLIO_RELEASE_KEY_PASSWORD=...
```

The keystore must sit outside the repository; the build refuses one inside it.

## Roadmap

- **1.1.0 — Bifold (done):** Folio, renamed and set up for the Galaxy Z Fold 8.
- **1.2.0 — Shizuku (done):** optional Shizuku features above.
- **Next:** testing the Shizuku features on real Fold 8 hardware (hotspot is the toggle most likely to need work) and continued Fold 8 polish.

Folio's own roadmap lives in the [original repo](https://github.com/McCal-Codes/folio).

## Credits

- **[McCal-Codes](https://github.com/McCal-Codes)** and the Folio contributors, for Folio, the launcher Bifold is built on.
- Third-party libraries and assets as listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- Bifold changes by Josh Reid ([@itxjobe](https://github.com/itxjobe)).

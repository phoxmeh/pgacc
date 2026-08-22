# Android CAT Controller

A Jetpack Compose Android app for controlling ham radio transceivers via CAT (Computer Aided Transceiver) protocol over USB serial or a network rigctld daemon.

## Features

- **Frequency display** — 9-digit VFO readout with per-digit tap-to-increment/decrement
- **Mode & bandwidth** — per-rig mode buttons with flrig-style labels (CW-U/CW-L, RTTY-U/RTTY-L, DATA-U/DATA-L, FM-N, AM-N); filter bandwidth dropdown polled live from the radio
- **VFO control** — VFO A/B select, A→B/B→A copy, swap, split
- **Signal meters** — S-meter (RX), RF power / SWR / ALC (TX)
- **Sliders with ±step buttons** — RF power, AF gain, RF gain / squelch (FM), MIC gain, IF shift, CW speed/pitch; each slider can be nudged one step at a time
- **IF shift** — enable toggle + ±1200 Hz slider; value polled live and synced with the radio
- **DSP toggles** — noise blanker, noise reduction, auto-notch, speech processor, VOX, antenna tuner
- **TUNE macro** — hold-to-transmit button (shown when PTT is armed); caps power at 10 W, switches to CW, keys the radio, restores mode and power on release
- **PTT** — CAT, RTS, or DTR; separate serial port supported for Digirig-style interfaces
- **Profiles** — named connection profiles stored in a local Room database; one profile auto-loads on launch
- **Rotation** — follows the system auto-rotate / rotation-lock setting

## Supported connection types

| Type | Description |
|---|---|
| **USB direct** | USB OTG to a Digirig, Signalink, or any FTDI/CP21xx/CH34x CAT interface |
| **rigctld** | Network connection to a `rigctld` daemon (hamlib); caps are discovered live via `\dump_caps` |

## Supported radios

The static device table (`RadioDeviceList.kt`) currently includes the **Yaesu FT-891** with full per-rig accuracy (mode codes, EX sideband registers, SH bandwidth tables sourced from flrig). The driver and caps framework is protocol-generic (Yaesu CAT, Icom CI-V, Kenwood CAT stubs present); the rigctld path supports any radio hamlib knows about.

## Requirements

- Android 8.0+ (API 26)
- USB OTG cable + CAT interface, **or** network access to a running `rigctld` instance

## Building

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires Android SDK with `compileSdk = 35` and a JDK 17+ toolchain (Gradle wrapper handles the rest).

## Architecture

```
RadioDriver (interface)
├── YaesuCatDriver   — Yaesu CAT ASCII protocol (FT-891 / FT-991 / FT-DX series)
├── IcomCivDriver    — Icom CI-V binary protocol
├── KenwoodCatDriver — Kenwood CAT protocol
└── RigctldDriver    — hamlib rigctld network socket; discovers caps live
```

`RigCaps` describes what a rig supports (mode list, bandwidth tables, feature flags). Static caps in `RadioDeviceList` are sourced from hamlib and flrig; for the rigctld path they are merged with the live `\dump_caps` response (static bandwidth tables win when they have more entries than the 3-value hamlib summary).

`RadioMode` uses two labels: `label` (hamlib wire string — never change) and `displayLabel` / per-rig `modeDisplayLabels` (flrig-style UI label).

The poll loop runs every 400 ms and reads frequency, mode (including EX sideband register for CW/RTTY/DATA L vs U on the FT-891), bandwidth (SH0), S-meter, TX meters, and IF shift.

## Key dependencies

- [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) — FTDI / CP21xx / CH34x / CDC USB serial
- Jetpack Compose Material 3
- Hilt (dependency injection)
- Room (profile database)
- Kotlin Coroutines + StateFlow

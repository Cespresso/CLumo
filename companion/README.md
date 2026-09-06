# CLumo Companion

[English] | [日本語 (README.ja.md)](README.ja.md)

The firmware and app for using CLumo together with the CLumo Android app. The
device's four functions map one to one onto the app's four functions, and
switching between them, as well as setting durations, happens in the app.

| Directory | Contents |
|---|---|
| [`firmware/`](firmware/) | BLE firmware. The BLE protocol specification lives here too |
| [`android/`](android/) | The CLumo Android app |

Most of what you need to know is already in the app's help pages. This README
therefore concentrates on what the device shows before the app connects, and on
what it does while the phone is away.

In the pictures below the left button is orange and the right one is white. Read
them as whatever filament you printed yours with.

## Getting started

1. Open the [installer page](https://cespresso.github.io/CLumo/), choose the companion firmware, connect the device over USB, and flash.
2. Download `clumo.apk` from the [latest release](https://github.com/Cespresso/CLumo/releases/latest) and install it on a phone running Android 8.0 or newer.
3. Power the device and put it near the phone.
4. Open the app, tap "Find CLumo", and tap the CLumo it finds. Enter passkey `123456` when asked.

From then on, opening the app reconnects on its own.

## When you power it on

The device first shows its connection state, and switches to a function's screen
once the app is connected and ready.

| Screen | What it means |
|:---:|---|
| <img src="img/link-lost.svg" width="84" alt="Thick corner in the top-left"> | Not connected. Shown for about a second after power-on and right after a disconnect, then the device returns to the function it was last using. |
| <img src="img/link-lost-bonded.svg" width="84" alt="Thick corner in the top-left with a dot in the bottom-right"> | Also not connected. The dot in the bottom-right means a phone has been paired before. |
| <img src="img/connecting.svg" width="182" alt="The corner-and-dot screen alternating with every pixel off"> | Connecting. Blinks from the moment the phone connects until the app has finished reading the device's state. |

## The four functions

Switching functions is a matter of picking one in the app. The device's buttons
never change it. The device remembers the last function and comes back to it
after a power cycle. Brightness is a slider in the app, and the device remembers
that too.

| Screen | Function | What it does |
|:---:|---|---|
| <img src="img/countdown-44.svg" width="84" alt="Lower part of the matrix lit"> | **Pomodoro** | Alternates work and break. The durations are set in the app and start out at 25 and 5 minutes. |
| <img src="img/countdown-full.svg" width="84" alt="Every pixel lit"> | **Timer** | A one-shot countdown from 00:01 to 59:59. When time is up it blinks until you do something. |
| <img src="img/display-heart.svg" width="84" alt="Heart"> | **My Displays** | Shows an 8x8 picture drawn in the app. A heart, a smile, and a star come preloaded. |
| <img src="img/visualizer.svg" width="84" alt="Eight bars of different heights"> | **Visualizer** | Eight bars move with the audio playing on the phone. Sensitivity is adjusted in the app. |

Pomodoro and Timer show the remaining time as 64 pixels. They start fully lit and
go out from the top-left toward the bottom-right. When a pomodoro phase ends the
matrix flashes three times and the next phase starts on its own.

## While the phone is away

Losing the connection does not stop the device. The connection screen shows for
about a second, then the function's screen comes back.

| Function | What the device does on its own |
|---|---|
| **Pomodoro** | Keeps counting. Orange starts, pauses and resumes. White resets. |
| **Timer** | Keeps counting. Orange starts, pauses and resumes, and after completion starts the same duration again. White cancels. |
| **My Displays** | Keeps the last picture sent, even through a power cycle. Switching pictures goes through the app, so the buttons do nothing while it is away. |
| **Visualizer** | The bars fall and go dark once audio stops arriving. The buttons do nothing. |

While connected, orange and white step to the next and previous picture in My
Displays, and raise and lower the sensitivity in Visualizer.

## Permissions

| Permission | When it is asked | What it is for |
|---|---|---|
| Nearby devices (Bluetooth) | On first launch | Finding and connecting to CLumo |
| Microphone | The first time you start the visualizer | Picking up the audio being played |
| Notifications (Android 13 and newer) | The first time you start the visualizer | The notification of the service that keeps the connection |

## Widgets

Two home-screen widgets are included. Both act on the "main CLumo", the one
starred in the device list.

| Widget | Size | What it does |
|---|:---:|---|
| **CLumo controls** | 4x1 | Start, pause, reset and cancel for Pomodoro and Timer. Reconnects when the device is not connected. |
| **CLumo** | 2x2 | Mirrors the device's face. Tapping it opens the app. |

## If something goes wrong

| Symptom | Cause | Fix |
|---|---|---|
| "Find CLumo" finds nothing | The device runs the standalone firmware, which has no Bluetooth | Flash the companion firmware from the installer |
| "This CLumo firmware is not supported" | The companion firmware is out of date | Flash the latest companion firmware from the installer |

For anything else, follow the app's dialogs. To pair again from scratch, forget
CLumo in Android's Bluetooth settings first, then connect.

## For developers

- BLE protocol specification and firmware build: [`firmware/README.md`](firmware/README.md)
- App build: [`android/README.md`](android/README.md)

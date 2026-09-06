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
| <img src="img/link-lost.svg" width="84" alt="Thick corner in the top-left"> | Never paired. A freshly flashed device waits on this screen. Press orange or white and it goes straight to Pomodoro or Timer, usable on its own. |
| <img src="img/link-lost-bonded.svg" width="84" alt="Thick corner in the top-left with a dot in the bottom-right"> | Paired but not connected. Shown for about a second after power-on and right after a disconnect, then the device returns to the function it was last using. |
| <img src="img/connecting.svg" width="182" alt="The corner-and-dot screen alternating with every pixel off"> | Connecting. Blinks from the moment the phone connects until the app has finished reading the device's state. |

## The four functions

Switching functions is a matter of picking one in the app. The device's buttons
never change it. The device remembers the last function and comes back to it
after a power cycle. Brightness is a slider in the app, and the device remembers
that too.

| Screen | Function | What it does |
|:---:|---|---|
| <img src="img/pomodoro-idle.svg" width="84" alt="Hourglass"> | **Pomodoro** | Alternates work and break. The durations are set in the app and start out at 25 and 5 minutes. |
| <img src="img/timer-idle.svg" width="84" alt="Clock face"> | **Timer** | A one-shot countdown from 00:01 to 59:59. When time is up it blinks until you do something. |
| <img src="img/display-heart.svg" width="84" alt="Heart"> | **My Displays** | Shows an 8x8 picture drawn in the app. A heart, a smile, and a star come preloaded. |
| <img src="img/visualizer.svg" width="84" alt="Eight bars of different heights"> | **Visualizer** | Eight bars move with the audio playing on the phone. Sensitivity is adjusted in the app. |

Pomodoro and Timer show their icon while idle. Once started, the remaining time is
64 pixels: they start fully lit and go out from the top-left toward the
bottom-right.

| Screen | What it means |
|:---:|---|
| <img src="img/countdown-44.svg" width="84" alt="Lower part of the matrix lit"> | About a third of the way through. Work, break, and timer all look the same. A pause freezes the picture where it is. |
| <img src="img/countdown-full.svg" width="84" alt="Every pixel lit"> | The end-of-phase flash. Pomodoro flashes three times and moves to the next phase on its own; Timer keeps blinking until you do something. |

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
| "Connection lost", over and over, on a device that used to work | The device was reflashed and lost its side of the pairing | Forget CLumo in Android's Bluetooth settings, then connect again |

A reflashed device introduces itself as a new one, but the app recognises it as the
same CLumo, so its name, colors, and main-device setting carry over. Enter the passkey
within 30 seconds; after that the device gives up on the pairing and you start over. A device you no
longer use can be removed from the list with "Remove this device" in the device
screen's menu. The app cannot remove the Android pairing, so forget it in Bluetooth
settings separately.

For anything else, follow the app's dialogs.

## For developers

- BLE protocol specification and firmware build: [`firmware/README.md`](firmware/README.md)
- App build: [`android/README.md`](android/README.md)

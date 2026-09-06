# CLumo Companion Firmware

Firmware for CLumo, an ESP32-C3 desk gadget with an 8x8 LED matrix (MAX7219)
and two physical buttons. It pairs with the CLumo Android companion app over
BLE; the device's four modes map 1:1 to the app's functions.

This document is the source of truth for the BLE protocol (v2).

## Hardware

| Component  | Detail                                        |
|------------|-----------------------------------------------|
| MCU        | ESP32-C3 (`riscv32imc-esp-espidf`)            |
| LED matrix | MAX7219 over SPI: GPIO8 SCLK, GPIO9 CS, GPIO10 MOSI |
| Buttons    | Main = red, GPIO3. Sub = white, GPIO4 (pull-up, active low) |

## Modes

| Value | Mode       | Description | Buttons |
|-------|------------|-------------|---------|
| 0     | Pomodoro   | Work/break countdown with app-settable durations. The matrix is a 64-pixel progress bar. | Main: start/pause/resume. Sub: reset. |
| 1     | Timer      | One-shot countdown configurable from `00:01` to `59:59`. At `00:00`, the matrix blinks every 400 ms until operated. | Main: start/pause/resume/restart. Sub: cancel. |
| 2     | Display    | Shows the last 8-byte bitmap committed to the DISPLAY characteristic (see preview/commit below). Persisted in NVS across reboots; blank until the first bitmap arrives. | Reported over BUTTON; the app cycles its saved patterns. |
| 3     | Visualizer | Bar visualizer driven by column heights streamed to the DISPLAY characteristic. The matrix stays blank until data arrives; bars rise instantly, fall smoothly, and decay to zero when data stops. | Reported over BUTTON; the app steps visualizer sensitivity. |

Modes are selected by the Android companion app through the MODE characteristic;
buttons never change the mode. A mode change immediately shows the selected
mode's normal frame without an icon splash. The current mode is persisted in NVS
and restored on boot. Firmware upgrading from protocol v1 migrates the old
Display value `1` to `2` and Visualizer value `2` to `3`, deriving the result
into the `MODE2` key and leaving the pre-v2 `MODE` and `MODE_SCHEMA` keys
read-only forever. Each NVS setter commits on its own, so migrating in place
across two keys could be interrupted between them; because `1` maps to `2` and
`2` maps to `3`, re-running the migration on its own output would silently turn
Display into Visualizer. Deriving into a separate key makes the migration
idempotent: an interrupted boot recomputes the same value from the same
untouched inputs. Every accepted mode change is pushed to subscribers of the
MODE characteristic.

Buttons register on release after 50 ms of debounce; there is no long press. In
Pomodoro and Timer the firmware acts on the press itself, so those modes stay
usable while disconnected. In Display and Visualizer the firmware only notifies
BUTTON, so those presses do nothing while disconnected.

All mode handlers live for the process lifetime. Pomodoro and Timer continue
counting while another mode is visible and resume from the same state when selected
again. Pomodoro work/break durations, Timer configured duration, brightness, Display
bitmap, and current mode are persisted in NVS. Countdown execution state is not
persisted across reboot; both countdowns boot idle with their stored durations.

While disconnected, the matrix briefly shows the link-loss icon and then returns
to the current mode's standalone frame. The connection icon keeps blinking until a
connected client finishes its encrypted GATT initial sync.

## Build & flash

Requirements:

- Rust **nightly** with the `rust-src` component (see `rust-toolchain.toml`)
- ESP-IDF **v5.2.2** (fetched automatically by `esp-idf-sys`; requires Python >= 3.10)
- Target `riscv32imc-esp-espidf`, linker `ldproxy`, flasher `espflash`

```bash
# Install tooling once
cargo install ldproxy espflash

# Build, flash, and monitor
cargo run
```

## Testing

`lib.rs` exposes the mode-agnostic modules (`countdown`, `display_state`,
`display_commit_policy`, `mode_values`, `settings_values`, `visualizer_values`) so
they can be unit tested on the host, without ESP-IDF. `--target` must override the
crate's default ESP32 target:

```bash
cargo test --lib --target <host-triple>   # e.g. aarch64-apple-darwin, x86_64-unknown-linux-gnu
```

CI runs this on every push and PR (`firmware-ci.yml`). `handlers/`, `mode.rs`, and
`main.rs` depend on ESP-IDF and are not covered by host tests.

## Pairing

- Security: BLE bonding with MITM protection, passkey **123456** (DisplayOnly).
- All readable and writable characteristics require an encrypted (paired) connection.
  BUTTON is notify-only, and NimBLE's auto-generated CCCD is subscribable without
  encryption, so the firmware instead withholds button notifications until a client
  has completed the encrypted initial sync.
- A connected client blocks advertising, so a client that connects but never
  completes that encrypted initial sync (bonds but never reads DEVICE_ID, or isn't
  CLumo's app at all) is force-disconnected after 60s, so a real client isn't
  locked out until power-cycle.

## BLE protocol v2

### State ownership

CLumo (the device) is the sole source of truth for everything it runs or persists;
the app reads and reflects that state rather than assuming it, and reconciles from
a fresh read on every reconnect. Everything the app owns locally never reaches the
device at all. Nothing is owned by both sides: where two representations of the
same fact exist (Display's live preview vs. its committed frame), one is always an
explicit, expiring projection of the other, never an independent second source
(see preview/commit below).

| State | Owner | On reconnect |
|---|---|---|
| Mode | Device (NVS `STATE`) | App re-reads MODE and adopts it as observed; an unexpired (<3s) pending tap is re-sent, not discarded, until the device confirms it |
| Brightness | Device (NVS `STATE`) | Same as Mode |
| Pomodoro/Timer: running state, phase, remaining time | Device only; not persisted, resets to idle on reboot | App re-reads POMODORO/TIMER |
| Pomodoro durations, Timer configured duration | Device (NVS, one namespace per handler) | App re-reads POMODORO/TIMER |
| Display: which 8-byte frame is on the matrix | Device (NVS `DISPLAY`, committed frame only; a live preview is never persisted) | App re-reads DISPLAY, which returns the committed frame (see preview/commit below) |
| Display: pattern names and the app's saved-pattern library | App only; the device has no concept of a pattern, only bytes | Not applicable; the app matches its library against the frame it reads by content |
| Visualizer sensitivity, automatic low-volume boost | App only; a local audio-processing parameter, never sent to the device | Not applicable |
| Device alias, appearance (colors) | App only; cosmetic and local | Not applicable |
| Primary device selection | App only; local | Not applicable |
| Device identity (UUID) | Device (NVS `DEVICE`, generated once on first boot, read-only) | App re-reads DEVICE_ID |

Rule of thumb: if losing power should not lose it, the device owns it and persists
it; if it is purely how the app presents the device (a name, a color, a saved
pattern's name), the app owns it and never sends it.

### Service

| Item             | Value |
|------------------|-------|
| Service UUID     | `455aa9f0-2999-43de-81b4-54e0de255927` |
| Advertising name | `CLumo-XXXX` where `XXXX` is the uppercase hex of device ID bytes 0 and 1 |

### Characteristics

| Name       | UUID | Properties | Payload |
|------------|------|------------|---------|
| MODE       | `681285a6-247f-48c6-80ad-68c3dce18586` | READ, WRITE, NOTIFY | 1 byte: mode `0..=3`. Write switches mode. A same-value Display write commits the current preview. Firmware notifies after an accepted mode change. Invalid values are ignored. |
| DISPLAY    | `681285a6-247f-48c6-80ad-68c3dce18585` | READ, WRITE, WRITE_NR | 8 bytes, interpreted by the current mode (see below). Ignored in Pomodoro and Timer modes. READ returns the last **committed** frame, never a live preview. |
| POMODORO   | `681285a6-247f-48c6-80ad-68c3dce18587` | READ, WRITE, NOTIFY | Write: command (below). Read/notify: 6-byte status (below). |
| BRIGHTNESS | `681285a6-247f-48c6-80ad-68c3dce18588` | READ, WRITE, NOTIFY | 1 byte: MAX7219 intensity, clamped to `0..=15`. Firmware echoes the applied value via notify. |
| DEVICE_ID  | `681285a6-247f-48c6-80ad-68c3dce18589` | READ | 16 bytes: stable UUIDv4 device identifier, generated on first boot and persisted in NVS. |
| TIMER      | `681285a6-247f-48c6-80ad-68c3dce1858a` | READ, WRITE, NOTIFY | Write: command (below). Read/notify: 5-byte status (below). |
| BUTTON     | `681285a6-247f-48c6-80ad-68c3dce1858b` | NOTIFY | 2 bytes `[mode, button]`: a button press in a mode the firmware does not handle itself (see below). |

**Add new characteristics at the end of the service, never in the middle.** NimBLE
assigns ATT handles in creation order, so inserting a characteristic ahead of an
existing one shifts that one's handle. A client that already bonded reconnects from
its cached GATT database and keeps using the old handles, so it silently reads the
wrong attribute. This is what broke reconnection entirely when BUTTON was first
added ahead of DEVICE_ID. Appending leaves every existing handle untouched, so clients
upgrading from older firmware keep working; they just do not see the new
characteristic until their cache is refreshed. The companion app forces exactly one
refresh when a known-optional characteristic is missing, which is how the new
features come alive after a firmware upgrade without re-pairing.

### DISPLAY payload interpretation

- **Display mode (2)**: row bitmap. Byte 0 = top row, byte 7 = bottom row.
  Within a byte, bit 7 (MSB) = leftmost column, bit 0 = rightmost column.
- **Visualizer mode (3)**: column heights. Byte 0 = leftmost column, byte 7 =
  rightmost column. Each byte is a height `0..=8` (values above 8 are clamped).
- **Pomodoro (0) and Timer (1)**: ignored.

Use WRITE_NR (write without response) for high-rate visualizer and Display-preview
streaming.

### Display preview and commit

Legacy v2 clients get the original behavior: every DISPLAY write is persisted.

A client that supports high-rate preview opts in, once per Display entry, by writing
the current `MODE = 2` value after initial sync, or by writing `MODE = 2` twice when
entering Display. The opt-in does not carry across a mode change: leaving and
re-entering Display resets it, and the next preview write auto-commits until the
client repeats the handshake. Once opted in, later DISPLAY writes are visible
immediately but are not persisted. Another same-value `MODE = 2` write commits the
current preview. An uncommitted preview is discarded on disconnect, mode change, or
after 5 seconds without another preview, and the last committed bitmap is restored.
The Android companion performs this handshake automatically.

ESP-NimBLE does not expose request-vs-command metadata to the server callback, so
the explicit same-mode MODE write is the commit boundary. Leaving legacy writes
durable lets live preview avoid wearing NVS without changing the behavior of
already-installed v2 clients.

### BUTTON notify payload (2 bytes)

| Index | Field    | Values |
|-------|----------|--------|
| 0     | `mode`   | mode the press happened in: `2` = Display, `3` = Visualizer |
| 1     | `button` | 0 = main (red), 1 = sub (white) |

Emitted only in Display and Visualizer mode. Pomodoro and Timer presses are
consumed by the firmware and never notified. The firmware holds no state for
these presses: it notifies only after a client has completed the encrypted
initial sync, and a press outside that window is simply lost rather than queued.

Companion apps are expected to treat main as "next / increase" and sub as
"previous / decrease" for whatever that mode presents.

### POMODORO commands (writes)

| Bytes | Command |
|-------|---------|
| `[0x01]` | Start (from idle) or resume (from paused). No-op while running. |
| `[0x02]` | Pause. No-op unless running. |
| `[0x03]` | Reset: state = idle, phase = work, remaining = work duration. |
| `[0x10, work_min, break_min]` | Set durations in minutes. Both values are clamped to `1..=99` and persisted in NVS. Applied immediately when idle; otherwise the new values take effect from the next reset or phase change. |

Every accepted command results in a status notify, including no-ops as a status
echo. Pomodoro keeps running in the background while another mode is shown, so
Reset is accepted from any mode; Start, Pause, and Set durations are processed
only while the device is in Pomodoro mode (ignored, with a status echo,
otherwise).

### POMODORO status (read value and notify payload, 6 bytes)

| Index | Field       | Values |
|-------|-------------|--------|
| 0     | `state`     | 0 = idle, 1 = running, 2 = paused |
| 1     | `phase`     | 0 = work, 1 = break |
| 2..3  | `remaining` | remaining seconds of the current phase, big-endian u16 |
| 4     | `work_min`  | work duration in minutes |
| 5     | `break_min` | break duration in minutes |

The firmware notifies on state or phase changes, once per second while running,
and after every accepted command. When a phase completes, it plays a short
full-screen blink, switches phase, and keeps running.

### TIMER commands (writes)

| Bytes | Command |
|-------|---------|
| `[0x01]` | Start from idle, resume from paused, or restart the configured duration after completion. No-op while running. |
| `[0x02]` | Pause. No-op unless running. |
| `[0x03]` | Cancel: state = idle and remaining = configured duration. The configured duration is retained. |
| `[0x10, minutes, seconds]` | Set duration while idle. Minutes and seconds must each be `0..=59`, and `00:00` is rejected. While running, paused, or completed, the setting is left unchanged and the current status is echoed. Accepted values are persisted in NVS. |

Every accepted command results in a status notify, including no-ops as a status
echo. The timer keeps running in the background while another mode is shown, so
Cancel is accepted from any mode; Start, Pause, and Set duration are processed
only while the device is in Timer mode (ignored, with a status echo,
otherwise).

### TIMER status (read value and notify payload, 5 bytes)

| Index | Field         | Values |
|-------|---------------|--------|
| 0     | `state`       | 0 = idle, 1 = running, 2 = paused, 3 = completed |
| 1..2  | `remaining`   | remaining seconds, big-endian u16 |
| 3     | `minutes`     | configured minutes (`0..=59`) |
| 4     | `seconds`     | configured seconds (`0..=59`) |

The firmware notifies on state changes, once per second while running, and after
every accepted command. At completion, remaining is `0` and the matrix blinks
on/off every 400 ms until Start/Cancel is received or either button is pressed.

### Countdown LED render rule

Pomodoro and Timer use a 64-pixel countdown progress bar. Companion apps that
mirror the device display must use exactly:

```
lit = ceil(remaining_secs * 64 / total_secs)   // clamped to 0..=64
```

For Pomodoro, `total_secs` is the current phase duration. For Timer, it is the
configured duration. Pixels are indexed row-major from the top-left
(`index = row * 8 + column`, bit 7 = leftmost column); the last `lit` pixel
indices are ON. As time passes, pixels turn off from the top-left toward the
bottom-right. Idle shows all 64 pixels lit, and a paused frame remains static.

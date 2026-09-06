# CLumo Companion Firmware

Firmware for CLumo, an ESP32-C3 desk gadget with an 8x8 LED matrix (MAX7219)
and two physical buttons. It pairs with the CLumo Android companion app over
BLE; the device's three modes map 1:1 to the app's functions.

This document is the source of truth for the BLE protocol (v1).

## Hardware

| Component  | Detail                                        |
|------------|-----------------------------------------------|
| MCU        | ESP32-C3 (`riscv32imc-esp-espidf`)            |
| LED matrix | MAX7219 over SPI: GPIO8 SCLK, GPIO9 CS, GPIO10 MOSI |
| Buttons    | Red = GPIO3, White = GPIO4 (pull-up, active low) |

## Modes

| Value | Mode       | Description | Buttons |
|-------|------------|-------------|---------|
| 0     | Timer      | Work/break countdown with app-settable durations. The matrix is a 64-pixel progress bar. | Red short: start/pause. Red long: reset. |
| 1     | Display    | Shows the last 8-byte bitmap written to the DISPLAY characteristic. Persisted in NVS across reboots; blank until the first bitmap arrives. | None |
| 2     | Visualizer | Bar visualizer driven by column heights streamed to the DISPLAY characteristic. Gravity model: bars rise instantly, fall smoothly, and decay to zero when data stops. | None |

A **white button long press** cycles Timer → Display → Visualizer → Timer,
showing the next mode's icon briefly. The current mode is persisted in NVS and
restored on boot. Every mode change (BLE write or button) is pushed to
subscribers of the MODE characteristic.

Note: mode handlers are recreated on every mode switch. Leaving Timer mode
discards a running countdown — the firmware notifies an idle timer status when
this happens.

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

## Pairing

- Security: BLE bonding with MITM protection, passkey **123456** (DisplayOnly).
- All characteristics require an encrypted (paired) connection.

## BLE protocol v1

### Service

| Item             | Value |
|------------------|-------|
| Service UUID     | `455aa9f0-2999-43de-81b4-54e0de255927` |
| Advertising name | `CLumo-XXXX` where `XXXX` is the uppercase hex of device ID bytes 0 and 1 |

### Characteristics

| Name       | UUID | Properties | Payload |
|------------|------|------------|---------|
| MODE       | `681285a6-247f-48c6-80ad-68c3dce18586` | READ, WRITE, NOTIFY | 1 byte: mode `0..=2`. Write switches mode. Firmware notifies on **any** mode change (BLE or button). Invalid values are ignored. |
| DISPLAY    | `681285a6-247f-48c6-80ad-68c3dce18585` | READ, WRITE, WRITE_NR | 8 bytes, interpreted by the current mode (see below). Ignored in Timer mode. |
| TIMER      | `681285a6-247f-48c6-80ad-68c3dce18587` | READ, WRITE, NOTIFY | Write: command (below). Read/notify: 6-byte status (below). |
| BRIGHTNESS | `681285a6-247f-48c6-80ad-68c3dce18588` | READ, WRITE, NOTIFY | 1 byte: MAX7219 intensity, clamped to `0..=15`. Firmware echoes the applied value via notify. |
| DEVICE_ID  | `681285a6-247f-48c6-80ad-68c3dce18589` | READ | 16 bytes: stable UUIDv4 device identifier, generated on first boot and persisted in NVS. |

### DISPLAY payload interpretation

- **Display mode (1)** — row bitmap: byte 0 = top row, byte 7 = bottom row.
  Within a byte, bit 7 (MSB) = leftmost column, bit 0 = rightmost column.
  The last bitmap is persisted in NVS and restored on reboot.
- **Visualizer mode (2)** — column heights: byte 0 = leftmost column, byte 7 =
  rightmost column. Each byte is a height `0..=8` (values above 8 are clamped).
- **Timer mode (0)** — ignored.

Use WRITE_NR (write without response) for high-rate visualizer streaming.

### TIMER commands (writes)

| Bytes | Command |
|-------|---------|
| `[0x01]` | Start (from idle) or resume (from paused). No-op while running. |
| `[0x02]` | Pause. No-op unless running. |
| `[0x03]` | Reset: state = idle, phase = work, remaining = work duration. |
| `[0x10, work_min, break_min]` | Set durations in minutes. Both values are clamped to `1..=99` and persisted in NVS. Applied immediately when idle (remaining is recomputed); otherwise the running phase finishes with its old duration and the new values take effect from the next reset/phase change. |

Every accepted command results in a status notify (even no-ops, as a status
echo). Timer commands are processed only while the device is in Timer mode;
in other modes they are ignored.

### TIMER status (read value and notify payload, 6 bytes)

| Index | Field       | Values |
|-------|-------------|--------|
| 0     | `state`     | 0 = idle, 1 = running, 2 = paused |
| 1     | `phase`     | 0 = work, 1 = break |
| 2..3  | `remaining` | remaining seconds of the current phase, big-endian u16 |
| 4     | `work_min`  | work duration in minutes |
| 5     | `break_min` | break duration in minutes |

Notify timing:

- on every state or phase change (including changes triggered by the red button),
- once per second while running,
- as an echo after every accepted TIMER command.

The characteristic value is kept up to date, so READ always returns the
current status. Idle status is `[0, 0, work_min*60 (BE u16), work_min,
break_min]` — i.e. the full work duration remaining.

When a work or break phase completes, the firmware plays a short full-screen
blink animation, switches to the other phase, and keeps running.

### Timer LED render rule

The matrix is a 64-pixel countdown progress bar. Companion apps that mirror
the device display must use exactly:

```
lit = ceil(remaining_secs * 64 / phase_total_secs)   // clamped to 0..=64
```

Pixels are indexed row-major from the top-left (index = row * 8 + column,
bit 7 = leftmost column); pixel indices `< lit` are ON. Idle shows the full
work duration, i.e. all 64 pixels lit. While paused the current frame is held
static.

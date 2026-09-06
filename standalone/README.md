# CLumo Standalone Firmware

Standalone firmware for CLumo, a desk gadget built around an ESP32-C3
(Seeed Studio XIAO ESP32C3), an 8x8 LED matrix (MAX7219), and two buttons.

There is no radio, no app, and no pairing: plug in power and everything works with just
the two buttons. Three modes are built in, and the current mode is persisted
to flash (NVS), so the device comes back in the same mode after a power cycle.

## Modes

| # | Mode | Description |
|---|----------|-------------|
| 0 | Pet | A tiny digital pet. It gets hungry over time, changes mood, blinks, and occasionally looks around. |
| 1 | Pomodoro | 25-minute work / 5-minute break timer. The 64 pixels drain from the top-left toward the bottom-right as a phase passes, break included. Blinks on each phase change. |
| 2 | Dice | Slot-style dice roll with a deceleration animation that lands on a random 1-6. |

## Button controls

Long press = hold for 1 second or more.

| Button | All modes |
|--------|-----------|
| White long press | Switch to the next mode (icon splash, then the mode screen) |

| Mode | Red short press | White short press |
|----------|--------------------------|-------------------|
| Pet | Feed the pet | Poke the pet |
| Pomodoro | Start / pause / resume | Reset to idle |
| Dice | Roll the dice | (none) |

## Hardware

- MCU: ESP32-C3 (Seeed Studio XIAO ESP32C3)
- LED matrix: MAX7219 over SPI (GPIO8 SCLK, GPIO9 CS, GPIO10 MOSI)
- Buttons: red = GPIO3, white = GPIO4 (internal pull-up, active low)

## Build & flash

### Requirements

- Rust nightly with the `rust-src` component. If you use rustup,
  `rust-toolchain.toml` installs it automatically; `espup` works too. The target
  is `riscv32imc-esp-espidf`, built with `-Z build-std`.
- `ldproxy` and `espflash`:

  ```bash
  cargo install ldproxy espflash
  ```

- Python >= 3.10 (required by the ESP-IDF tooling).
- ESP-IDF v5.2.2, fetched and built automatically by `esp-idf-sys` on the
  first build. No manual installation is needed, but expect the first build to
  take a while.

### Flash

Connect the board over USB-C and run:

```bash
cargo run
```

This builds the firmware, flashes it with `espflash`, and opens the serial
monitor.

## Recovery: entering bootloader mode manually

If flashing stops working, put the board into bootloader mode by hand. This
can happen after misconfiguring GPIO 18/19: those are the USB D-/D+ pins, so
never reconfigure them.

The XIAO ESP32C3 has two small buttons on the edge opposite the USB-C
connector: **BOOT** (left, GPIO9) and **RESET** (right).

1. Press and hold **BOOT**.
2. While holding it, press and release **RESET**.
3. Release **BOOT**.
4. Run `cargo run` to flash.

Alternatively:

1. Unplug the USB cable.
2. Plug it back in while holding **BOOT**.
3. Release **BOOT** and run `cargo run`.

# CLumo assembly guide

English | [日本語](README.ja.md)

The main skills you need are soldering pin headers and crimping JST XH contacts. The connections are drawn in the wiring diagram in step 3.

You could build it with no connectors and solder every wire directly, but the difficulty and the cable routing make that a poor option. This guide uses connectors throughout.

## What you need

Figure 1 shows the parts laid out. The photo does not show the full count of JST parts, so check the tables below for quantities.

![All parts: printed parts, XIAO, matrix module, wires, tactile switches, JST parts](img/01-parts.jpg)

Figure 1: The printed parts and electronics laid out. Only some of the JST parts are shown.

### Electronics

| Part | Qty | Notes |
|---|---|---|
| Seeed XIAO ESP32-C3 | 1 | |
| MAX7219 8x8 LED matrix module | 1 | The common 32 mm square module |
| Tactile switch, 6x6 mm | 2 | Four-leg type |
| Heat-shrink tubing | a little | To cover the GND splice |

### JST XH 2.54 mm

A compatible kit covers everything. All board headers are straight.

| Part | Qty | Used for |
|---|---|---|
| 5-pin board header | 2 | XIAO left column / matrix IN |
| 2-pin board header | 1 | XIAO right column |
| 5-pin housing | 2 | XIAO left column / matrix side |
| 2-pin housing | 1 | XIAO right column |
| 3-pin housing | 2 | Buttons. Only the two outer positions are used; the middle stays empty |
| Crimp contacts | 16 | |

### Wire

About 5 cm for every run. When I built mine, anything shorter was awkward to handle. You can go a bit longer, but too long and it will not fit in the case.

| Type | Qty | Run |
|---|---|---|
| Coloured | 4 | XIAO to matrix (3V3 / D10 / D9 / D8) |
| Coloured | 2 | XIAO to each button (D1 / D2) |
| Black | 4 | GND. One from the XIAO, splitting into three for the matrix and both buttons |

### Printed parts

There are five kinds, shown in Figure 2. Each button is two parts, a bushing and a cap, and printing them in a different colour gives the box an accent. The 3D data is [`clumo.3mf`](../models/clumo.3mf).

![Printed parts: outer shell, back panel, white tray, grey frame, button bushings and caps](img/02-printed-parts.jpg)

Figure 2: The printed parts are the shell, back panel, tray, frame, and two button sets.

| Part | Colour in photos | Role |
|---|---|---|
| Shell | dark green | Two round button holes on top, large openings front and back |
| Back panel | dark green | Closes the back opening. The slot is the USB-C port |
| Tray | white | A shallow box the matrix drops into. Its floor is the diffuser |
| Frame | grey | Carries the XIAO and the switches. All wiring happens here |
| Button bushing | yellow / orange | A ring that fits the round hole in the shell |
| Button cap | yellow / orange | The part you press |

### Tools

Soldering iron, a crimp tool for JST XH, cutters, and a wire stripper.

## Overview

1. Solder a header to the matrix and drop it into the tray
2. Solder headers to the XIAO
3. Build the harness
4. Fit the switches into the frame
5. Snap the XIAO into the frame
6. Connect everything inside the frame
7. Mate the tray and the frame
8. Slide it into the shell
9. Fit the buttons
10. Fit the back panel
11. Flash the firmware

## 1. Solder a header to the matrix and drop it into the tray

The module has two identical header rows. The 5-pin header goes on the row whose silkscreen reads VCC / GND / **DIN** / CS / CLK (Figure 3). The other row is DOUT, the output for chaining a second module. A header on that side lights nothing.

![Back of the matrix module next to the white tray, with a 5-pin header on the DIN side](img/03-matrix-header.jpg)

Figure 3: The header goes on the side marked DIN. Here the left column is DOUT and the right column is DIN.

With the header on, drop the module into the tray LEDs down. It is seated when the back of the board faces up and the connector sticks out over the rim of the tray.

## 2. Solder headers to the XIAO

Neither header starts at the end of its column, so check the positions before soldering. Figure 4 is the underside, the face with the pin names printed on it.

![Underside of the XIAO ESP32-C3, with solder on the five pads from GND to D8 on the left and on D1 and D2 on the right](img/05-xiao-headers.jpg)

Figure 4: The 5-pin header covers GND through D8 on the left column; the 2-pin header covers D1 and D2 on the right.

| Column | Header | Pads | Goes to |
|---|---|---|---|
| The 5V column | 5-pin | GND / 3V3 / D10 / D9 / D8 | matrix (GND is shared with the buttons) |
| The D0 column | 2-pin | D1 / D2 | buttons |

## 3. Build the harness

Follow the wiring diagram in Figure 5. Four coloured wires run straight between the XIAO's 5-pin and the matrix's 5-pin. GND is different: one black wire leaves the XIAO and splits three ways to the matrix and both buttons. Cover the splice with heat-shrink.

![Wiring diagram: XIAO GND / 3V3 / D10 / D9 / D8 to the matrix VCC / GND / DIN / CS / CLK, and D1 / D2 to the two buttons](img/clumo-wiring.png)

Figure 5: Five wires go to the matrix and two to the buttons; only GND splits from one wire into three.

The buttons use 3-pin housings, but only the two outer positions are crimped; the middle stays empty. Two XH pitches of 2.54 mm match the spacing of the tactile switch legs. Each button housing therefore carries one coloured wire from D1 or D2 and one black wire from the GND splice. The finished harness looks like Figure 6.

![The finished harness: two 5-pin, one 2-pin, and two 3-pin housings, with the GND splice covered in heat-shrink](img/06-harness.jpg)

Figure 6: The harness has five housings, and only the black GND wire branches.

## 4. Fit the switches into the frame

The top plate of the frame has two square pockets for the switches. Push a tactile switch into each from the outside (Figure 7).

![Two tactile switches seated in the top plate of the frame, legs in the slots on either side](img/07-switches-top.jpg)

Figure 7: Pushed in from the outside, each switch's legs fall into the slots either side.

Now push the harness's 3-pin housings into the same slots from inside the frame. The crimp contacts press against the switch legs, and the fit of the printed slots alone holds the housing (Figure 8). Nothing is soldered.

![The frame seen from below, with two 3-pin housings pushed up into the slots](img/08-switch-housings.jpg)

Figure 8: The 3-pin housings go in from below and are held by the fit of the slots.

Orient each housing so its two crimped positions meet the switch legs. If the empty middle position lines up with the middle slot, it is the right way round.

The contacts poke out of the slots next to the legs, so you can solder them to the switch if you like. The PLA is right next to the joint, though, and it is hard to do without melting it. The crimped fit works well on its own, so this is your call.

## 5. Snap the XIAO into the frame

Inside the frame is a cradle with catches for the XIAO. Point the USB-C toward the back, the open side of the frame, keep the shielded face up, and press the board in. It is a little stiff, but it snaps into place (Figure 9).

![The XIAO snapped into the frame, USB-C facing forward and both headers facing up](img/09-xiao-mounted.jpg)

Figure 9: The XIAO snaps in with its USB-C facing the back.

There is only one correct orientation. Reversed, the USB-C will not line up with the hole in the back panel.

## 6. Connect everything inside the frame

Space inside the frame is tight; you are working in the gap between the top plate and the XIAO.

> The XIAO's 5-pin connector and the button housings overlap, so there is a knack to it. There may be other ways, but this is how I do it.
> 1. Push both 3-pin button housings in first (step 4)
> 2. Plug the D1 / D2 housing into the XIAO's 2-pin header
> 3. Last, plug the 5-pin into the XIAO, sliding it in from the USB-C side

When done it looks like Figure 10. Leave the five wires for the matrix trailing out the top.

![Inside the frame, both XIAO connectors and both button housings are connected, with the matrix cable coming out the top](img/10-wired.jpg)

Figure 10: Everything except the matrix cable is connected.

## 7. Mate the tray and the frame

Plug the harness into the matrix's 5-pin connector and bring the tray and frame together face to face (Figure 11).

![The white tray and grey frame joined by a JST connector, side by side](img/11-tray-mated.jpg)

Figure 11: A single connector joins the tray and the frame.

## 8. Slide it into the shell

The shell has two openings. The slightly smaller one at the front is the LED window; the wide one at the back is where the assembly goes in. Line up these four things and push it in through the wide opening (Figure 12).

| Align | Position |
|---|---|
| Tray (white) | small front opening |
| Frame (grey) | wide back opening |
| Face with the two round holes | up |
| USB-C | down |

![The assembly inside the shell, with the switches visible through the top holes and the XIAO's USB-C visible at the back](img/12-in-shell.jpg)

Figure 12: Seated correctly, the switches show through the top holes and the USB-C sits low in the back opening.

## 9. Fit the buttons

The buttons go in from the outside. Drop a cap into each round hole on top (Figure 13), then twist a bushing ring in around it (Figure 14). The bushing is a press fit in the hole and keeps the cap from falling out.

![Two caps sitting in the round holes on top of the shell, without bushings](img/13-caps.jpg)

Figure 13: The caps go into the holes first.

![Bushing rings fitted around the caps, completing the buttons](img/14-bushings.jpg)

Figure 14: Twisting in the bushings finishes the buttons.

## 10. Fit the back panel

The back panel goes on with the USB-C slot at the bottom (Figure 15).

![The back panel fitted, USB-C slot at the bottom](img/15-back-panel.jpg)

Figure 15: The back panel fits with the USB-C slot downward.

## 11. Flash the firmware

Plug a USB-C cable in through the back and flash the firmware. When the display comes on, you are done (Figure 16).

![The finished CLumo with a USB cable attached, showing a heart on the matrix](img/16-done.jpg)

Figure 16: Once flashed, the display comes on.

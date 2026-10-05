# Demo script (about 6 minutes)

Start the app with `./mvnw javafx:run`. To reset the demo data, close the app and delete the `data/` folder.

## 1. The board (1 min)
1. Point out the live clock, the **LIVE** dot and "Updated" time.
2. Click the **TIME** header, then click it again: the arrow flips and the order reverses. Try **GATE** and **STATUS**.
3. Click a row: the drawer slides open (terminal, check-in, baggage belt, aircraft). Click again to close it.
4. Click the pin on any flight: it jumps to the top and stays there whatever you sort by.
5. Hover a status badge and a gate to show the tooltips.
6. Wait for the simulation: a status badge pops when it changes, and a yellow banner slides in for gate changes.

## 2. My Flight and check-in (1 min)
1. Press **Ctrl+F** (works from any screen), type `BK7F3A`, press Enter.
2. Show the countdown tiles, gate and seat. Click the **ALSO BOOKED** chip to open the passenger's second booking.
3. Click **Check in**: a boarding pass with a barcode appears. Click Check in again after re-searching to show it is gone.
4. Search `nope` to show the friendly not-found message (a `FlightNotFoundException` underneath).

## 3. Wayfinding (30 s)
1. On the board, click any row, then **Route to gate**.
2. Show the pulsing "You are here" marker, the animated route, the walking time and the directions.
3. Pick a different flight in the side-panel dropdown and watch the route change.

## 4. Staff mode (2 min)
1. Click **Staff Login**, enter `staff123`. A **Staff Panel** tab appears.
2. Change a gate to one already in use (for example change KQ 412 to `A3`): the **gate occupied** error shows.
3. Change it to a free gate: the banner announces it on the board and the audit log records it.
4. Delay a flight by 30 minutes: the board shows the new estimated time in red.
5. Cancel a flight with a reason: check the board row and its details drawer.
6. Click **Undo last action** a few times and watch the board and audit log revert.
7. Add a flight (for example `KQ777`, Kenya Airways, Cairo, `CAI`, a time, gate `C4`, a wide-body). Try a bad gate such as `Z9` to show validation.
8. Switch the progression strategy between **Demo** and **Realistic**, pause and resume the simulation.

## 5. Accessibility and persistence (1 min)
1. Press **Ctrl+1**, use the arrow keys to move between rows, **Enter** to expand, **P** to pin, **R** for the route.
2. Toggle **Aa** (larger text) and **HC** (high contrast).
3. Pin a flight, make a staff change, close the app and reopen it: everything is exactly as you left it
   (saved in `data/fids.json`).
4. Run `./mvnw test` to show the 65 passing unit tests.

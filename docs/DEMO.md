# Njeri — Live Demo & Features

**Role:** introduce the airport problem, then show how SkyScope helps passengers and staff.
**Target time:** about 5–6 minutes.
**Audience takeaway:** one live system supports the public departures board, a ticketed passenger, wayfinding, and airport staff.

## Before the audience arrives

- Start the app and confirm the board is visible.
- Make sure MySQL is connected if the team plans to show persistence later.
- Confirm booking **BK7F3A** is not already checked in. The database keeps check-in state between launches.
- Have a free gate and an unused flight number available for the add-flight step.
- Simulation changes are event-driven and can be random. For a reliable live banner, log in to Staff Mode during setup, select **Demo**, then log out and leave the app running with the Board visible. The simulator ticks about every 12 seconds; do not pause the presentation waiting for a random event.

## 1. Quick intro — 30 seconds

> “At a busy airport, passengers can miss a gate change when they rely on static boards, and staff need a quick way to keep flight information current. SkyScope gives three perspectives on the same live data: a general passenger can follow departures, a ticketed passenger can manage a booking, and airline staff can update flights.”

## 2. Board View — about 1 minute

1. Point out the live clock, **LIVE** indicator, and last-updated time.
2. Click the **TIME** heading to sort, then click again to reverse the order. Repeat quickly with **GATE** or **STATUS**.
3. Click a flight row to open its details drawer. Point out terminal, check-in counter, baggage belt, and aircraft. Click again to close it.
4. Pin a flight so it stays at the top when the board is sorted.
5. Show a live status change or simulation banner if one appears. Explain that the board refreshes from flight events; the simulation uses the same service path as staff changes.

**Transition:** “The board is useful to everyone in the terminal. Now I’ll follow one passenger’s booking.”

## 3. My Flight — about 1 minute

1. Press **Ctrl+F**, enter **BK7F3A**, and press Enter.
2. Point out the flight, gate, seat, boarding group, and countdown.
3. Click the **also booked** shortcut to show the passenger’s other booking, then return to **BK7F3A**.
4. Click **Check in**. Show the confirmation and generated boarding pass with its barcode.

**Say:** “The booking reference gives the passenger a direct path to their flight, check-in, and boarding pass.”

## 4. Wayfinding — about 30 seconds

1. Return to the board, select a flight, and choose **Route to gate** from its details drawer.
2. Show the animated route from **You are here**, the highlighted gate, and the estimated walking distance and time.
3. Briefly point out the directions beside the map.

**Transition:** “Passengers can find their gate. Staff can keep the information accurate when plans change.”

## 5. Staff Mode — about 2 minutes

1. Click **Staff Login** and enter **staff123**. The Staff Panel opens.
2. Select a flight and choose a free gate, then click **Change gate**. Point out the board notification and audit entry.
3. Select an eligible flight, choose a delay length such as **30 minutes**, choose a reason such as **Bad weather**, and apply the delay.
4. Click **Undo last action** immediately. The delay is reversed, showing the staff undo flow.
5. Add a flight: choose an airline, enter unused digits such as **777**, select **Cairo**, choose a departure time, free gate, and aircraft, then submit. Fill all required fields.
6. Switch the simulation strategy between **Demo** and **Realistic**. Demo advances flights quickly; Realistic follows the clock. Leave the preferred setting running.

**Say:** “Staff actions are validated and recorded. The same live board reflects the change, and the most recent staff action can be undone.”

## 6. Accessibility and handoff — about 30 seconds

1. Toggle **HC** in the header to show high-contrast mode.
2. Press **Ctrl+1** to return to the board. Use the arrow keys to select a flight, **Enter** to open its details, **P** to pin it, and **R** to route to its gate.
3. Hand off to Brian:

> “That is the passenger and staff experience. Brian will show how the architecture and OOP design support these features.”

## If a step goes wrong

- **No live banner appears:** keep moving through the demo. The simulation is timed and random; explain that flight events refresh the board and show a banner when the event calls for one.
- **BK7F3A is already checked in:** use a clean presentation database or restore that booking before the demo. MySQL state is independent of data/fids.json once the database has been populated.
- **The new flight number already exists:** choose a different unused airline and digit combination.
- **The staff action fails:** choose an active flight and a gate shown as available. Gate and status rules are enforced by the application.

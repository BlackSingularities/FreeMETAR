## 2.0.0 — Aircraft-matched displays (from OpenPFD)

- Versioning: releases are now cut from `vX.Y.Z` tags instead of every push to `main`; the app and Link carry the tag version.
  Apps on 1.10.x cannot detect 2.0.0 automatically — install it once by hand from the release page.

- PFD and ND/MFD now match the aircraft: Airbus A320, Boeing 737, 787, ATR 72, Embraer E190, Garmin G1000 (with SVT terrain),
  analog six-pack (3 per row), R44 and glider panels. The aircraft profile picked in the cockpit menu drives the displays too.
- Displays use full simulator data from the new Link `avionics` block: baro/STD, FMA modes, MCP/FCU bugs, flight director,
  ILS/VOR/GPS deviation, DME, markers, minimums, speed limits, gyro instrument readings, radios and transponder.
- Separate speed brake and wheel brake axes (wheel brakes on the ground only; L2/R2 on a gamepad).
- Cockpit: header removed; recording, camera, checklists and the aircraft menu sit next to the panel tabs.
- Camera switching falls back to VIEW_MODE when MSFS ignores CAMERA STATE.
- Live flight strip above other tabs (tap to open the cockpit); collapsible tab rail on tablets.
- 3D map: thin flight-path line at altitude instead of the curtain, and the aircraft drawn at its altitude.
- Logbook: every landing in a flight (touch-and-go, multi-airport) is scored separately.
- Removed route planning (plan editor, map planning, typed routes, saved routes, import/export) and SimBrief;
  the flight plan now comes from the simulator GPS (next/previous waypoint positions added to the Link `avionics.gps` block).
- Map: compact button group, simpler top bar; the bottom value bar is gone (the live flight strip shows the same).

## next CI release — Transparent application icon

- Use the transparent application mark in launcher, splash and existing branding surfaces.
- Preserve alpha in legacy and adaptive Android icons; refresh themed icon resources.


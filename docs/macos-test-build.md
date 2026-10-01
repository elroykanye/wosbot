# macOS test build

This is an unsigned community test build. It has not been validated against a
real Whiteout Survival account on macOS yet.

## Before opening Frostguard

1. Install Tesseract with `brew install tesseract`.
2. Install MuMuPlayer for Mac and choose **MuMuPlayer for Mac**
   in Frostguard. Frostguard uses MuMu's `mumutool` to start, stop, and locate
   each device's ADB port.
3. Profile number `0` maps to MuMu device 0, profile `1` to device 1,
   and so on.

This build supports Apple Silicon Macs only.

If Frostguard cannot find `mumutool`, open MuMuPlayer's **Developer → Open
Command-Line Tool** once. Advanced testers can also launch Frostguard with
`FROSTGUARD_MUMUTOOL_PATH` set to the full path printed in that Terminal window.

Because the app is not signed or notarized, macOS may block the first launch.
Right-click the app, choose **Open**, then confirm **Open**.

## What to report

Send the Mac model/chip, macOS version, emulator name, and which build you used.
Then report these separately:

- Did Frostguard open?
- Did it detect each running emulator?
- Could it capture the game screen and tap correctly?
- Did OCR work?
- Which task did you run, and what happened?

Attach `frostguard.log` when something fails, after removing account names and IDs.

Frostguard is maintained upstream by Shederator and contributors. This test build
keeps the original AGPL-3.0 license and source history.

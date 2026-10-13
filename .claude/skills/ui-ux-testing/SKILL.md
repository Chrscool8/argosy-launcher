---
name: ui-ux-testing
description: Drive the running app on an Android emulator and visually verify UI/UX by capturing screenshots and reading them back. Use to confirm a screen renders, navigate a flow (e.g. first-run/quickstart), check dual-modality input, or catch visual regressions. Knows the technical limits of emulator testing and when the device is still the final authority.
---

# UI/UX Testing (emulator, visual)

Closes a real feedback loop: drive the app with adb input, capture a screenshot, and
READ the PNG back (the Read tool renders images, so the screen is actually seen), then
assert against pixels + logcat. This catches UI regressions, ghost settings, crashes, and
flow breakage that log-reading alone misses.

This is a full stand-in for a physical device when none is connected (or to avoid tying one
up): no hardware needed, and on Apple Silicon the arm64 image runs the native cores. Reserve a
real device for the hardware-bound axes only (see Limitations).

For the raw adb input/log primitives on a connected device, see `debug-device`. This skill
is the emulator + visual-verification layer and its caveats.

## Core principle: offload to a subagent

Screenshots flood the main context fast. Run the device-driving in a subagent (Agent tool):
it does all the adb + screencap + Read cycles in ITS context and returns only the outcome
and findings. Give the agent the gotchas below in its prompt so it does not relearn them.

Always downscale before reading: the device is 1080x2400, but many-image requests cap each
image at 2000px and reject larger ones. Resize first:
`sips -Z 1500 shot.png --out shot_s.png` (macOS built-in), then Read the `_s` file.

## Environment

- Apple Silicon: use an `arm64-v8a` system image (API 35, `google_apis`). It runs the app's
  arm64 native libretro cores NATIVELY, so software cores and UI both work.
- Install SDK pieces: `sdkmanager "emulator" "system-images;android-35;google_apis;arm64-v8a"`,
  then `avdmanager create avd`. Boot headless:
  `emulator -avd <name> -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot`.
- Wait for boot: poll `adb -s emulator-5554 shell getprop sys.boot_completed` == 1.
- Two devices appear (physical + emulator); always target `-s emulator-5554`.
- Install today's debug APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## The loop

1. Drive: `input tap X Y`, `input keyevent <code>`, `input text "<one char>"`.
2. Capture: `adb exec-out screencap -p > shot.png`; downscale; Read.
3. Observe the rendered screen.
4. Assert non-visual state via `logcat` (sync no_op, FATAL EXCEPTION, etc.).

Verify the result screenshot after EVERY tap. Coordinates are easy to miss; do not assume a
tap landed where intended.

## Input gotchas (learned the hard way)

- `input text` MANGLES strings: it drops adjacent duplicate chars (`//`->`/`, `mm`->`m`) and
  autocorrects words (`nendo`->`nedo`). Fix: send ONE character per `input text` call.
  `cmd clipboard set-text` is NOT implemented on the emulator image, so paste is unavailable.
- Coordinate mapping: the screenshot reports original 1080x2400 but is displayed scaled.
  Multiply displayed coords by the stated factor. Immersive mode (the app hides system bars)
  shifts the whole layout UP, so coords differ between a status-bar screen and an immersive one.
- Prefer gamepad keyevents for NAVIGATION (dpad + confirm) over guessing pixel coords; it both
  avoids mistaps and exercises the controller path. Reserve taps for the touch-modality check.
- Argosy is a HOME/launcher app. `monkey -c LAUNCHER` does NOT foreground it; launch the
  activity directly: `am start -a android.intent.action.MAIN -n
  com.nendo.argosy.debug/com.nendo.argosy.ArgosyActivity`. The HOME filter lives on the
  `com.nendo.argosy.MainActivity` activity-alias, which the "Available as Home App" setting
  disables.
- First immersive entry pops Android's "Viewing full screen" overlay ON TOP of the app; dismiss
  it ("Got it") or the first screenshot is the overlay, not the screen.

## Verification recipes (tie to the coupling map)

These turn `coupling-map.md` proof obligations into emulator-backed checks:
- Dual-modality: drive the same nav via `tap` AND via `keyevent`, screenshot both, confirm parity.
- Ghost setting: toggle a setting, screenshot, then assert the behavior actually changed on screen.
- Save-sync: trigger sync, grep logcat for `no_op` twice (needs a reachable RomM backend).
- No-crash sweep: watch logcat for `FATAL EXCEPTION` while scripting a navigation.

## Skipping the wizard (when first-run is not what you are testing)

Driving the full quickstart by hand is slow and brittle (URL typing, 60s pair-code race,
permission grants, folder/platform/core steps). When the target is some OTHER screen, get to a
connected Home directly instead of replaying onboarding:

- Mint a client token via the API (no 8-box pairing) using the helper at
  `scripts/romm-auth.sh` (invoke it by that path from the project root):
  `scripts/romm-auth.sh call POST /api/client-tokens
  '{"name":"agent-test","scopes":["me.read","roms.read","platforms.read","assets.read",
  "assets.write","devices.read","devices.write","collections.read"]}'` -> grab `raw_token`.
- Seed the app state so `isFirstRun` is false: it needs the RomM base URL + that raw token in
  prefs AND `firstRunComplete = true`. Routing is `isFirstRun = !firstRunComplete &&
  !hasExistingConfig` (ArgosyViewModel).

Mechanism:
- USE THE DEBUG SEED HOOK (`DebugSeedReceiver`, debug source set only, excluded from release).
  It calls `connectWithToken` (persists URL + token) then `setFirstRunComplete()`. The prefs
  Flow re-emits, `isFirstRun` recomputes to false, and routing navigates to Home with NO relaunch.
  1. Mint a raw token (see above), capture `raw_token`.
  2. `adb shell am broadcast -n com.nendo.argosy.debug/com.nendo.argosy.debugtools.DebugSeedReceiver \
       --es url "$ROMM_BASE_URL" --es token "<RAW_TOKEN>"`
  (Trigger by component, not action, to dodge implicit-broadcast restrictions.)
- FALLBACK (fragile): `run-as com.nendo.argosy.debug` to edit SharedPreferences/DataStore files
  directly. Brittle (paths + schema drift); only if the hook is unavailable.

Caveat: seeding trades fidelity for speed -- it SKIPS storage/platform/core-download setup, so
the library may be empty and cores absent. Only seed when onboarding itself is not under test;
to validate the wizard, drive it for real. Do NOT use `am start` as a shortcut -- it re-runs
startup routing and abandons an incomplete wizard rather than completing it.

## Limitations and drawbacks (READ THIS)

The emulator RAISES THE FLOOR; it is NOT the final authority. It reliably catches UI
regressions, ghost settings, crashes, flow breakage, and sync logcat. It does NOT faithfully
reproduce:
- HW libretro cores (Dolphin/Flycast) and GL context loss on backgrounding.
- Save paths on real `/Android/data` storage.
- Real GPU behavior and shader/driver paths.

So for the WALKTHROUGH-FORCED axes in `coupling-map.md` (save-sync, RomM), a green emulator
run is NOT proof. The device remains the final authority there.

Other drawbacks observed:
- `uiautomator dump` is nearly BLIND on this app: custom-focus Compose exposes almost no
  semantics/testTags, returning empty clickable nodes. Screenshots are the only reliable
  verification surface; do not rely on the view tree.
- `adb install -g` grants ALL runtime permissions, which MASKS permission-request UX (e.g. the
  camera prompt on the QR scanner never appears). Install WITHOUT `-g` to test permission flows.
- Granting all-files access: `appops set --uid com.nendo.argosy.debug MANAGE_EXTERNAL_STORAGE
  allow`. The app re-checks on resume.
- Do NOT use `am start` to "refresh" mid-flow: it re-runs startup routing, which can bypass an
  incomplete wizard once config exists (`isFirstRun = !firstRunComplete && !hasExistingConfig`).
  To re-check a permission, return from the system settings screen via BACK, not a fresh launch.
- RomM pair codes expire in 60s. Mint the code (`scripts/romm-auth.sh call POST /api/client-tokens/<id>/pair '{}'`)
  immediately before entering it, and finish Connect fast.
- Builds are slow; build in the background and wait for the completion notification.
- Destructive resets (`pm clear`) need explicit user authorization, even on a test install.

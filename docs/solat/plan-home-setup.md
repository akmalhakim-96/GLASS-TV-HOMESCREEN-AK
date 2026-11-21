# "Pasang & jadikan Home" from the phone — design and plan

**Approved by the owner** ("tambah. ini lagi senang") after this shape was described in chat.

## Why
Installing Glass TV needs ADB (make it Home, overlay permission, the remote's Home/OK service). The
phone app already speaks ADB, so the owner can do the whole setup without a computer.

## What the button does (a port of `launcher/install-prebuilt.ps1`, same safeguard)
1. Refuse Android older than 8 (SDK < 26) and Fire TV, changing nothing.
2. Note where Home goes now; work out the stock launcher the same way the script does
   (known list `com.google.android.tvlauncher`, `com.google.android.apps.tv.launcherx`,
   `com.google.android.leanbacklauncher`, `com.mitv.tvhome`; Android's chooser `android` → the single
   known launcher among the HOME claimants; Glass TV already Home → `none`; anything else → stop).
3. Push the Glass TV APK bundled in the phone app (assets/glass-tv.apk) and `pm install -r`.
4. `appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow` (azan island).
5. Start Glass TV; check it is in front and not restarting (same pid twice, focus on it).
6. Disable the stock launcher, set Glass TV as Home, check Home resolves to it, press Home, check again.
7. Any failure from step 3 on → uninstall Glass TV, re-enable the stock launcher, set the original Home
   back, press Home; tell the owner in Malay.
8. Turn on HomeKeyService (remote's Home button on Xiaomi sticks, and OK to stop the azan), keeping
   other accessibility services; switch off High contrast text if on.
9. Then the form is read from the TV; the owner presses HANTAR to send zone, MP3s and the rest.

Not included: debloat (needs a reviewed per-model list; stays a computer step, optional).

## Files
- `remote/app/src/main/assets/glass-tv.apk` — copied from the launcher's release build (ruling:
  committed binary, 334 KB, so the repo builds the phone app without building the launcher first).
- `remote/.../HomeSetup.java` — pure decisions (tested) + `run(Context, String host, Progress)`.
- `remote/.../MainActivity.java`, layout — the button, a confirm dialog, progress in the status line.

## Tests (pure, `HomeSetupTest`)
- `stockFromHome`: `"com.mitv.tvhome/.MainActivity"` → `com.mitv.tvhome`.
- `alreadyGlassTv`: `"com.gathin.tvhome/.MainActivity"` → `none`.
- `chooserPicksTheOneKnownLauncher`: home `android`, claimants contain one known → it; two known → error.
- `unknownHomeStops`: `"com.other.launcher/.X"` → error.
- `sdkGate`: `"28"` ok, `"25"` refused, `"x"` refused.
- `healthyNeedsSamePidAndFocus`: `"123|123|mCurrentFocus=Window{… com.gathin.tvhome/…}"` true;
  different pids false; empty pid false; focus elsewhere false.
- `accessibilityKeepsOthers`: null/"" → service only; `"a/.B"` → `"a/.B:com.gathin.tvhome/.HomeKeyService"`;
  already present → unchanged.

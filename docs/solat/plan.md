# Glass TV Solat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add JAKIM prayer times to the Glass TV Home screen and play the owner's azan with a glass "island", a dimmed screen and centred text over whatever is playing, stopped by the first OK press.

**Architecture:** Pure-Java helpers (`Prayer*` classes) parse and reason about JAKIM data and are unit-tested on the JVM. A small Android layer fetches and caches the year's data, schedules one exact alarm for the next prayer, and on the alarm runs a foreground service that ducks other audio, plays the MP3 and shows a non-focusable overlay. `HomeKeyService` swallows the first OK while azan is active. `MainActivity` shows the pill, chips and Hijri date from the cache.

**Tech Stack:** Java 17, Android SDK 34 / minSdk 26, AGP 8.5.2, JUnit 4, `org.json` (test only), `java.time`.

**Spec:** `/home/user/glass-tv-remote/docs/superpowers/specs/2026-11-20-glass-tv-remote-design.md` (Part "Glass TV Solat"). Mockups: `/home/user/glass-tv-remote/docs/mockups/mockup-home-5app.png`, `mockup-azan-tengah.png`.

**Working copy:** `/home/user/cocombee/glass-tv` on a new local branch `solat`. Commits stay local (no push: the upstream repo is not ours).

## Global Constraints

- Release APK stays **under 400 KB** (`launcher/app/build/outputs/apk/release/app-release.apk`).
- All prayer-time maths uses `ZoneId.of("Asia/Kuala_Lumpur")`, never the stick's zone.
- JAKIM endpoint: `https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=year&zone=<ZONE>`. Response `{"prayerTime":[{"hijri":"1448-05-21","date":"01-Nov-2026",…,"fajr":"05:48:00","syuruk":…,"dhuhr":…,"asr":…,"maghrib":…,"isha":…}],"status":"OK!",…}`. Bad zone: `"status":"NO_RECORD!"`. Month abbreviations are Malay: `Jan Feb Mac Apr Mei Jun Jul Ogo Sep Okt Nov Dis`.
- Prayers with azan: `subuh, zohor, asar, maghrib, isyak` (JAKIM keys `fajr, dhuhr, asr, maghrib, isha`). Syuruk is never an azan and never shown as "next".
- New `config.json` keys and defaults, exactly: `prayer_zone` (none → feature off), `prayer_zone_name` (defaults to the zone code), `azan_prayers` (all five), `azan_text` = `"Telah masuk waktu solat fardu {waktu} bagi kawasan {zon} dan sekitarnya"`, `azan_subtext` = `"Marilah menunaikan solat"`, `azan_dim` 0/1/2 (default 1).
- Files in the external files dir: `azan.mp3`, optional `azan_subuh.mp3` (falls back to `azan.mp3`). No MP3 → island and text still shown, silent.
- An alarm that fires more than **2 minutes** after its prayer time does not play.
- No prayer data for today → no chips, no pill, no alarm. Never show invented times.
- Keys: every remote key behaves as before, except the **first** OK (DPAD_CENTER / ENTER / NUMPAD_ENTER) down+up while azan is active, which stops the azan.
- Copy is Malay; prayer display names: `Subuh, Zohor, Asar, Maghrib, Isyak`.
- No new listening ports. Network use is only the JAKIM fetch (plus the existing places pictures).

## Review Focus

- Isyak has passed today → the pill must show tomorrow's Subuh, and the alarm must target tomorrow's Subuh (test `nextAfterIsyakIsTomorrowsSubuh`, Task 1).
- Year boundary: on 31 Dec after Isyak with only this year's data cached → no "next" (no wrong time) and the store must want a refetch (tests `nextIsNullPastLastDay`, Task 1; `dueWhenFewDaysLeft`, Task 2).
- Stick wakes from standby or reboots long after a prayer time → no late azan, and the next alarm is rescheduled (test `latePlayIsSkipped`, Task 3; boot receiver in Task 3).
- Two alarms or a reschedule while azan is playing → no second player; a new start while active restarts nothing (test `AzanState` in Task 4).
- `config.json` carrying a wrong-type value (`"azan_dim":"gelap"`, `"azan_prayers":"maghrib"`) → defaults, not a crash (test `badTypesFallBack`, Task 2).

---

### Task 1: JAKIM parsing and "next prayer"

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/PrayerTimes.java`
- Modify: `launcher/app/build.gradle` (add `testImplementation 'org.json:json:20240303'`)
- Test: `launcher/app/src/test/java/com/gathin/tvhome/PrayerTimesTest.java`
- Fixture: `launcher/app/src/test/resources/sgr01-month.json` (copy of `/home/user/glass-tv-remote/ref/sgr01-month.json`, November 2026)

**Interfaces:**
- Produces:
  - `enum PrayerTimes.Prayer { SUBUH, ZOHOR, ASAR, MAGHRIB, ISYAK; String label(); String key(); }` — `label()` is the display name, `key()` the config name (`"subuh"`…).
  - `static PrayerTimes PrayerTimes.parse(String json) throws org.json.JSONException, IllegalArgumentException` (IAE when status is not `OK!` or the list is empty).
  - `static final ZoneId PrayerTimes.MY = ZoneId.of("Asia/Kuala_Lumpur")`
  - `ZonedDateTime at(LocalDate day, Prayer p)` — null when the day is missing.
  - `boolean has(LocalDate day)`; `LocalDate lastDay()`
  - `static final class Next { final Prayer prayer; final ZonedDateTime time; }`
  - `Next next(ZonedDateTime now)` — first prayer strictly after `now`, today then tomorrow; null if not in data.
  - `String hijri(LocalDate day)` — e.g. `"21 Jamadilawal 1448H"`; null when missing.

- [ ] **Step 1: Write the failing tests** (`PrayerTimesTest`, fixture loaded from the classpath)

```java
@Test public void parsesMalayMonthsAndTimes() {
  PrayerTimes t = load();
  assertEquals(ZonedDateTime.of(2026,11,1,5,48,0,0,PrayerTimes.MY), t.at(LocalDate.of(2026,11,1), Prayer.SUBUH));
  assertEquals(ZonedDateTime.of(2026,11,30,20,16,0,0,PrayerTimes.MY), t.at(LocalDate.of(2026,11,30), Prayer.ISYAK));
  assertEquals(LocalDate.of(2026,11,30), t.lastDay());
}
@Test public void parsesDisAndOgo() {  // inline two-day JSON with "31-Dis-2026" and "15-Ogo-2026"
  assertTrue(t.has(LocalDate.of(2026,12,31))); assertTrue(t.has(LocalDate.of(2026,8,15)));
}
@Test public void nextSkipsSyurukAndPicksZohor() {  // 1 Nov 06:08 → Zohor 13:00
  Next n = load().next(ZonedDateTime.of(2026,11,1,6,8,0,0,PrayerTimes.MY));
  assertEquals(Prayer.ZOHOR, n.prayer); assertEquals(13, n.time.getHour());
}
@Test public void nextAfterIsyakIsTomorrowsSubuh() {  // 1 Nov 21:00 → 2 Nov 05:48
  Next n = load().next(ZonedDateTime.of(2026,11,1,21,0,0,0,PrayerTimes.MY));
  assertEquals(Prayer.SUBUH, n.prayer); assertEquals(LocalDate.of(2026,11,2), n.time.toLocalDate());
}
@Test public void nextAtExactTimeIsTheFollowingOne() { /* 1 Nov 13:00:00 → ASAR */ }
@Test public void nextIsNullPastLastDay() { assertNull(load().next(ZonedDateTime.of(2026,11,30,21,0,0,0,PrayerTimes.MY))); }
@Test public void hijriInMalay() { assertEquals("21 Jamadilawal 1448H", load().hijri(LocalDate.of(2026,11,1))); }
@Test(expected = IllegalArgumentException.class) public void noRecordIsRejected() {
  PrayerTimes.parse("{\"prayerTime\":{\"data\":[\"No data\"]},\"status\":\"NO_RECORD!\"}");
}
```

- [ ] **Step 2: Run, expect FAIL** — `cd launcher && ./gradlew testReleaseUnitTest --tests '*PrayerTimesTest*'` → compilation failure (`PrayerTimes` missing).
- [ ] **Step 3: Implement `PrayerTimes`** — store `Map<LocalDate, LocalTime[5]>` and `Map<LocalDate,String>` hijri. Months: `{"jan","feb","mac","apr","mei","jun","jul","ogo","sep","okt","nov","dis"}` matched case-insensitively on the first 3 letters (also accept English `mar, may, aug, oct, dec`). Hijri months: `Muharam, Safar, Rabiulawal, Rabiulakhir, Jamadilawal, Jamadilakhir, Rejab, Syaaban, Ramadan, Syawal, Zulkaedah, Zulhijah`; day without leading zero.
- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** — `git commit -m "Parse JAKIM prayer times and find the next prayer"`

### Task 2: Settings from `config.json` and the cached year

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/PrayerSettings.java`
- Create: `launcher/app/src/main/java/com/gathin/tvhome/PrayerStore.java`
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/Config.java` (hold `final PrayerSettings prayer`, built from the same `json`; `PrayerSettings.fromJson(null)` when no overlay)
- Test: `launcher/app/src/test/java/com/gathin/tvhome/PrayerSettingsTest.java`

**Interfaces:**
- Consumes: `PrayerTimes.parse`, `PrayerTimes.Prayer`, `PrayerTimes.lastDay()`
- Produces:
  - `final class PrayerSettings { final String zone /* "" = off */; final String zoneName; final EnumSet<Prayer> azan; final String text; final String subtext; final int dim; boolean enabled(); static PrayerSettings fromJson(@Nullable JSONObject j); String textFor(Prayer p); String subtextFor(Prayer p); }` — `textFor` replaces `{waktu}` with `p.label()` and `{zon}` with `zoneName`.
  - `final class PrayerStore { static PrayerTimes load(Context c, String zone) /* null if none */; static boolean due(@Nullable PrayerTimes cached, String cachedZone, String zone, long fetchedAtMs, long nowMs, LocalDate today); static boolean refresh(Context c, String zone) /* blocking, call off the main thread */; }` — cache file `files/prayer.json` (internal `getFilesDir()`) plus prefs `prayer_zone`, `prayer_fetched_at`.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void defaults() { PrayerSettings s = PrayerSettings.fromJson(new JSONObject("{\"prayer_zone\":\"SGR01\"}"));
  assertTrue(s.enabled()); assertEquals("SGR01", s.zoneName); assertEquals(EnumSet.allOf(Prayer.class), s.azan); assertEquals(1, s.dim);
  assertEquals("Telah masuk waktu solat fardu Maghrib bagi kawasan SGR01 dan sekitarnya", s.textFor(Prayer.MAGHRIB)); }
@Test public void offWithoutZone() { assertFalse(PrayerSettings.fromJson(null).enabled()); }
@Test public void customTextAndSubset() { /* azan_prayers ["maghrib","isyak"], prayer_zone_name "Shah Alam", azan_text "{waktu} – {zon}" → "Maghrib – Shah Alam", azan contains only MAGHRIB, ISYAK */ }
@Test public void badTypesFallBack() { /* "azan_dim":"gelap", "azan_prayers":"maghrib", "azan_dim":7 → dim 1, all five */ }
@Test public void dueWhenZoneChanged() { assertTrue(PrayerStore.due(times, "WLY01", "SGR01", now, now, nov(1))); }
@Test public void dueWhenOlderThan30Days() { /* fetchedAt = now - 31 days → true; now - 1 day → false */ }
@Test public void dueWhenFewDaysLeft() { /* lastDay 30 Nov, today 24 Nov → true; today 20 Nov → false */ }
@Test public void dueWhenNothingCached() { assertTrue(PrayerStore.due(null, "", "SGR01", 0, now, nov(1))); }
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testReleaseUnitTest --tests '*PrayerSettingsTest*'`
- [ ] **Step 3: Implement** `PrayerSettings` (unknown prayer names ignored; empty resulting set → all five) and `PrayerStore` (`HttpURLConnection`, 15 s timeouts, UTF-8, parse before replacing the cache so a bad response never overwrites good data; write to a temp file then rename). Due rules: no cache, zone differs, fetched ≥30 days ago, or `lastDay` < today + 7 days. A failed fetch is retried no sooner than 1 hour later (pref `prayer_failed_at`).
- [ ] **Step 4: Run, expect PASS**; also run the full suite: `./gradlew testReleaseUnitTest`.
- [ ] **Step 5: Commit** — `git commit -m "Prayer settings from config.json and a cached JAKIM year"`

### Task 3: Scheduling the next azan

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/AzanScheduler.java`
- Create: `launcher/app/src/main/java/com/gathin/tvhome/AzanReceiver.java`
- Modify: `launcher/app/src/main/AndroidManifest.xml` (receiver for `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, and the internal alarm action `com.gathin.tvhome.AZAN`; permission `RECEIVE_BOOT_COMPLETED`)
- Test: `launcher/app/src/test/java/com/gathin/tvhome/AzanSchedulerTest.java`

**Interfaces:**
- Consumes: `PrayerTimes.next`, `PrayerSettings`, `PrayerStore.load`
- Produces:
  - `static PrayerTimes.Next AzanScheduler.nextAzan(PrayerTimes t, PrayerSettings s, ZonedDateTime now)` — the next prayer whose `azan` flag is on (walks forward past disabled ones), null if none in data.
  - `static boolean AzanScheduler.shouldPlay(long prayerAtMs, long nowMs)` — true when `0 ≤ now − prayerAt ≤ 120 000`.
  - `static void AzanScheduler.schedule(Context c)` — reads config + cache, sets one `setExactAndAllowWhileIdle(RTC_WAKEUP, …)` alarm with extras `prayer` (key) and `at` (ms), or cancels it.
  - `AzanReceiver`: on the alarm → if `shouldPlay` start `AzanService` (Task 4) then `schedule`; on the system broadcasts → `schedule`, and if `PrayerStore.due(…)` refresh on a background thread then `schedule` again; on action `com.gathin.tvhome.AZAN_TEST` → start `AzanService` now with the next prayer's name (for the owner's test, sent with `am broadcast -n com.gathin.tvhome/.AzanReceiver -a com.gathin.tvhome.AZAN_TEST`). The alarm uses an explicit `PendingIntent`.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void skipsDisabledPrayers() { /* azan only ISYAK, now 1 Nov 06:08 → ISYAK 20:09 */ }
@Test public void wrapsToTomorrowWhenTodayDisabled() { /* azan only SUBUH, now 1 Nov 06:08 → SUBUH 2 Nov */ }
@Test public void latePlayIsSkipped() { assertTrue(AzanScheduler.shouldPlay(0, 119_000)); assertFalse(AzanScheduler.shouldPlay(0, 121_000)); assertFalse(AzanScheduler.shouldPlay(10_000, 0)); }
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testReleaseUnitTest --tests '*AzanSchedulerTest*'`
- [ ] **Step 3: Implement** scheduler and receiver (receiver uses `goAsync()` for the refresh thread).
- [ ] **Step 4: Run, expect PASS**; `./gradlew assembleRelease` builds.
- [ ] **Step 5: Commit** — `git commit -m "Schedule one exact alarm for the next azan"`

### Task 4: Azan playback, overlay and the OK key

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/AzanService.java` (foreground service declared with `android:foregroundServiceType="mediaPlayback"`; permissions `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`)
- Create: `launcher/app/src/main/java/com/gathin/tvhome/AzanOverlay.java` (builds and removes the overlay views)
- Create: `launcher/app/src/main/java/com/gathin/tvhome/AzanState.java`
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/HomeKeyService.java` (OK interception at the top of `onKeyEvent`)
- Modify: `launcher/app/src/main/AndroidManifest.xml` (service, `SYSTEM_ALERT_WINDOW`, foreground-service permissions)
- Modify: `launcher/app/src/main/res/values/strings.xml` (notification channel name "Azan", island hint "OK · teruskan")
- Test: `launcher/app/src/test/java/com/gathin/tvhome/AzanStateTest.java`

**Interfaces:**
- Consumes: `PrayerSettings.textFor/subtextFor/dim/zoneName/zone`, `Prayer.label()`, `Config.dir(ctx)` for the MP3s
- Produces:
  - `final class AzanState { static boolean active(); static boolean begin() /* false if already active */; static void end(); static boolean swallowOk(int keyCode, int action) /* true for OK keys while active; on ACTION_UP returns true and requests stop */; static void setStopper(@Nullable Runnable r); }`
  - `AzanService` intents: start with extras `prayer`, `at`; action `com.gathin.tvhome.AZAN_STOP` stops.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void beginTwiceIsRefused() { assertTrue(AzanState.begin()); assertFalse(AzanState.begin()); AzanState.end(); }
@Test public void okIsSwallowedOnlyWhileActive() { assertFalse(AzanState.swallowOk(KEYCODE_DPAD_CENTER, ACTION_DOWN)); AzanState.begin();
  assertTrue(AzanState.swallowOk(KEYCODE_DPAD_CENTER, ACTION_DOWN)); assertFalse(AzanState.swallowOk(KEYCODE_VOLUME_UP, ACTION_DOWN));
  assertFalse(AzanState.swallowOk(KEYCODE_HOME, ACTION_DOWN)); assertFalse(AzanState.swallowOk(KEYCODE_BACK, ACTION_DOWN)); }
@Test public void okUpRunsStopperOnce() { /* stopper counter → 1 after DOWN+UP; after end(), a second OK is not swallowed */ }
```
(Use the int constants from `KeyEvent`; the JVM stub returns them as compile-time constants.)

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testReleaseUnitTest --tests '*AzanStateTest*'`
- [ ] **Step 3: Implement `AzanState`**, then `AzanService`: `startForeground` with a low-importance channel; `AzanState.begin()` or stop if already active; request focus `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` with `USAGE_MEDIA`; MediaPlayer on `azan_subuh.mp3` for SUBUH if present else `azan.mp3`; no file → stop after 60 s. On completion / stop: abandon focus, remove overlay, `AzanState.end()`, `stopSelf()`. `AzanOverlay`: only if `Settings.canDrawOverlays`; `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN`, MATCH_PARENT; dim layer alpha `{0.45, 0.62, 0.80}[dim]` black; centred texts per mockup (gold caps line "MAGHRIB · 7:12 PM", 30sp semibold main text, 14sp subtext); island 540×74 dp-equivalent rounded pill at top centre with dark translucent fill `#8C0A0C14`, 1 dp white 38% rim, gold mosque circle, title "Azan Maghrib · 7:12 PM", zone line, "OK · teruskan" chip; fade/slide in 300 ms (100 ms when `reduced_motion`). Font `R.font.outfit`.
- [ ] **Step 4: `HomeKeyService.onKeyEvent`** — first line: `if (AzanState.swallowOk(event.getKeyCode(), event.getAction())) return true;`
- [ ] **Step 5: Run all tests and build** — `./gradlew testReleaseUnitTest assembleRelease` → PASS.
- [ ] **Step 6: Commit** — `git commit -m "Play azan with a glass island and dimmed overlay; first OK stops it"`

### Task 5: Prayer times on Home

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/PrayerBar.java` (fills the pill, chips and Hijri text; pure formatting helpers are static)
- Modify: `launcher/app/src/main/res/layout/activity_main.xml` (Hijri `TextView @+id/hijriDate` in `dateBlock` under `@id/date`; pill `TextView @+id/prayerPill` as the first child of `topButtons`, not focusable; `LinearLayout @+id/prayerChips` as the last child of `heroShelf`, 5 chip `TextView`s built in code)
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java` (create `PrayerBar` in `onCreate` from `cfg.prayer`; update it in `minuteTick` and `onResume`; kick `PrayerStore.refresh` + `AzanScheduler.schedule` on a background thread when `due`; call `AzanScheduler.schedule(this)` in `onCreate`)
- Test: `launcher/app/src/test/java/com/gathin/tvhome/PrayerBarTest.java`

**Interfaces:**
- Consumes: `PrayerTimes`, `PrayerSettings`, `PrayerStore.load/due/refresh`, `AzanScheduler.schedule`
- Produces: `static String PrayerBar.countdown(Duration d)` and `static String PrayerBar.clock(LocalTime t, boolean h24)`.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void countdown() { assertEquals("7 min lagi", PrayerBar.countdown(Duration.ofMinutes(7)));
  assertEquals("7j 2m lagi", PrayerBar.countdown(Duration.ofMinutes(422))); assertEquals("1 min lagi", PrayerBar.countdown(Duration.ofSeconds(20))); }
@Test public void clock12and24() { assertEquals("1:10 PM", PrayerBar.clock(LocalTime.of(13,10), false)); assertEquals("13:10", PrayerBar.clock(LocalTime.of(13,10), true)); }
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testReleaseUnitTest --tests '*PrayerBarTest*'`
- [ ] **Step 3: Implement** per mockup `mockup-home-5app.png`: pill text "🕌 Zohor 1:10 PM · 7j 2m lagi" (countdown in `#FFCF7A`), pill background reuses the top buttons' glass look (`@style/TopButton` background) but `focusable=false`; chips 30 dp tall, label caps 11 sp at 70 % alpha + time 12.5 sp, next chip with gold tint `#4DFFBE5A` and rim `#E6FFD28C`. Feature off or no data for today → pill, chips and Hijri `GONE`. Chip times use the clock's 12/24 h setting (`DateFormat.is24HourFormat`).
- [ ] **Step 4: Run all tests and build**; check APK size < 400 KB: `ls -l app/build/outputs/apk/release/app-release.apk`.
- [ ] **Step 5: Render check** — none possible without a TV; note it for the owner's checklist.
- [ ] **Step 6: Commit** — `git commit -m "Show JAKIM prayer times, next-prayer pill and Hijri date on Home"`

### Task 6: Install script, first theme and hand-over pack

**Files:**
- Modify: `launcher/install-prebuilt.ps1` (after install, before the health checks: `appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow`; print a warning, not an error, if it fails)
- Create: `themes/solat-shah-alam/config.json`
- Create: `themes/solat-shah-alam/README.txt` (Malay: put `azan.mp3` / `azan_subuh.mp3` here before pushing)
- Modify: `tools/theme.ps1` (also push `azan.mp3` and `azan_subuh.mp3` from the theme folder when present, removing them on the TV when absent)
- Create (outside the repo): `/home/user/cocombee/glass-tv/glass-tv-solat.zip` and `SEMAK.md` checklist (Malay)

```json
{
  "columns": 5,
  "reduced_motion": true,
  "prayer_zone": "SGR01",
  "prayer_zone_name": "Shah Alam"
}
```

- [ ] **Step 1:** `python3 -c "import json;json.load(open('themes/solat-shah-alam/config.json'))"` → no error.
- [ ] **Step 2:** Edit both `.ps1` files; re-read them for PowerShell 5.1 pitfalls already noted in the repo (commands with quotes go on stdin via `--batch`).
- [ ] **Step 3:** Full build: `./gradlew testReleaseUnitTest assembleRelease`; copy APK to `launcher/glass-tv.apk`; zip as in the first hand-over (exclude `.git`, build dirs, docs images).
- [ ] **Step 4:** Write `SEMAK.md`: pill and chips on Home; test azan now with `tools\\venv\\Scripts\\python -I tools\\tv.py "am broadcast -n com.gathin.tvhome/.AzanReceiver -a com.gathin.tvhome.AZAN_TEST"` (Task 3); OK stops; Home/volume/Netflix work during azan; reboot stick → next azan still fires.
- [ ] **Step 5: Commit** — `git commit -m "Install overlay permission, Shah Alam theme and azan files"`

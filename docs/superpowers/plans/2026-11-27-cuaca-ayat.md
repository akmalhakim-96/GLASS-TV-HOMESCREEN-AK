# Weather, air quality and daily verse: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for
> tracking.

**Goal:** Home shows a weather and air-quality pill under the clock, and a daily Quran verse above the prayer
times on the right. The phone app gets three new settings for these.

**Architecture:** The launcher work is split into small classes:

- pure, unit-tested classes: `Weather` (parse and words), `WeatherZones` (zone → coordinates), `HomeExtras`
  (config keys) and `Verses` (the daily pick);
- a blocking fetcher, `WeatherStore`, which follows `PrayerStore`'s pattern and is called from
  `MainActivity`'s existing off-main-thread refresh while Home is in front;
- verse text that is generated once by `tools/ayat/fetch.py` into `res/raw/ayat.json`.

The phone app sends the keys in its Home section.

**Tech Stack:** Java, Android SDK 34 (launcher minSdk 26), org.json, JUnit 4; Python 3 for the verse script.

**Spec:** `docs/superpowers/specs/2026-11-27-cuaca-ayat-design.md`

## Global Constraints

- The launcher APK stays under 409600 bytes (CI check).
- There is no network while another app is in front, and no new service. Fetches run off the main thread.
- Hosts: `api.open-meteo.com` and `air-quality-api.open-meteo.com` on the TV. `api.quran.com` is used only at
  build time.
- Air quality is labelled `Udara` (ms) / `Air` (en), never "API".
- The Arabic text is Quran.com `text_imlaei`. The translations are Quran.com 39 (ms, Basmeih) and 20 (en,
  Sahih International), with footnotes stripped. No verse text is typed by hand.
- The config keys are `weather` (bool), `daily_verse` (bool) and `display_language` (`"ms"`/`"en"`, default
  `"ms"`).
- Commits carry no AI attribution (AGENTS.md).

## Review Focus

1. No internet at school in the morning. Expected: the pill shows the last reading for up to 3 hours, then
   hides, with no error text. Test: `WeatherTest.staleAfterThreeHours`.
2. The air-quality request fails but the weather request works. Expected: the pill shows the weather alone.
   Test: `WeatherTest.pillWithoutAir`.
3. A zone that is not in the table, or no zone at all. Expected: no weather, and no crash. Test:
   `WeatherZonesTest.unknownZone`.
4. 31 December and the leap day. Expected: the verse index stays valid. Test: `VersesTest.indexAcrossYearEnd`.
5. An old config with none of the new keys. Expected: the verse is on, weather is on when the zone is known,
   and the language is Malay. Test: `HomeExtrasTest.defaults`.

---

### Task 1: Weather logic (launcher)

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/Weather.java`
- Create: `launcher/app/src/main/java/com/gathin/tvhome/WeatherZones.java`
- Test: `launcher/app/src/test/java/com/gathin/tvhome/WeatherTest.java` and `WeatherZonesTest.java`

**Interfaces (produces):**

- `final class Weather.Reading { final int tempC; final int code; final boolean day; final int aqi; final long atMs; }`.
  `aqi` is -1 when unknown.
- `static Weather.Reading parse(String forecastJson, String airJson, long nowMs)`. It returns null when the
  forecast is bad. A bad `airJson` gives `aqi` = -1.
- `static String words(int code, boolean day, String lang)` and `static String icon(int code, boolean day)`,
  following the spec's table.
- `static String band(int aqi, String lang)`. The bands are 0–50 Baik/Good, 51–100 Sederhana/Moderate,
  101–200 Tidak sihat/Unhealthy, 201–300 Sangat tidak sihat/Very unhealthy, and 301+ Berbahaya/Hazardous.
- `static int bandColor(int aqi)`. The colours are green `0xFF4ADE80`, yellow `0xFFFACC15`, orange
  `0xFFFB923C`, red `0xFFF87171` and purple `0xFFC084FC`.
- `static boolean fresh(Reading r, long nowMs)`. It is true for an age of 3 hours or less.
- `static String weatherText(Reading r, String lang)` → `"⛅ 29° Berawan"`.
- `static String airText(Reading r, String lang)` → `"Udara 50 Sederhana"`. It returns `""` when `aqi` < 0.
- `static double[] WeatherZones.coords(String zone)`. It returns null when the zone is unknown. The table has
  every JAKIM zone in `Zones` (phone app), with the main town of each.

- [ ] **Step 1: Write the failing tests**

  The tests are: `parseBoth`, `pillWithoutAir`, `wordsAndIcons`, `bands`, `staleAfterThreeHours`, `unknownZone`
  and `shahAlam`. `shahAlam` asserts SGR01 is within 0.2° of (3.07, 101.52).
- [ ] **Step 2: Run them, and expect a compile failure**

  `cd launcher && ./gradlew testDebugUnitTest --tests '*Weather*'`
- [ ] **Step 3: Implement the classes, then run the tests, and expect a PASS**
- [ ] **Step 4: Commit**

### Task 2: Fetch and cache (launcher)

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/WeatherStore.java`
- Test: `WeatherTest.dueAfterAnHour`

**Interfaces (produces):**

- `static boolean due(long lastOkMs, long lastTryMs, long nowMs)`. It is true when the last good reading is
  60 minutes old or more, and the last try was 10 minutes ago or more.
- `static Weather.Reading cached(Context c)`.
- `static boolean refresh(Context c, String zone)`. It is blocking. It makes 2 GETs with 10-second timeouts,
  and saves only a parse that is not null.

- [ ] Write the failing test, implement, run the tests, and commit.

### Task 3: Verses data and pick

**Files:**
- Create: `tools/ayat/verses.txt` (one `surah:ayah` per line, about 60)
- Create: `tools/ayat/fetch.py`
- Create: `launcher/app/src/main/res/raw/ayat.json`
- Create: `launcher/app/src/main/java/com/gathin/tvhome/Verses.java`
- Test: `VersesTest`

**Interfaces (produces):**

- `ayat.json` is `[{"ref":"65:3","surah_ms":"At-Talaq","surah_en":"At-Talaq","ar":"…","ms":"…","en":"…"}]`.
- `static Verses parse(String json)`
- `Verse pick(LocalDate day)`. The index is `(day.getDayOfYear() - 1) % size`.
- `Verse { String ref, surahMs, surahEn, ar, ms, en }`
- `String heading(Verse v, String lang)` → `"AT-TALAQ 65:3"`.

- [ ] Choose the verses: short ones (the Arabic about 110 characters or less), on the spec's themes. Show the
  list to the owner.
- [ ] Run `fetch.py`. It needs `api.quran.com`, so ask for the host. It fails if any verse is missing a field
  or if the Arabic is over the length limit.
- [ ] Test, then implement `Verses`. The tests are `pickIsStable`, `indexAcrossYearEnd` and `headingUppercase`,
  and one test that loads the real `ayat.json` and checks every entry has all fields.
- [ ] Commit.

### Task 4: Config keys (launcher)

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/HomeExtras.java`
- Modify: `Config.java` (a new field `homeExtras`, read in `load`)
- Test: `HomeExtrasTest`

**Interfaces (produces):**

- `HomeExtras { final boolean weather, verse; final String lang; static HomeExtras fromJson(JSONObject j) }`
- `boolean weatherOn(String zone)`. It is the `weather` setting AND `WeatherZones.coords(zone) != null`.

- [ ] The tests are `defaults` (empty JSON → verse on, lang ms, weather on for SGR01, off for no zone) and
  `explicitOff`. Implement, run the tests, and commit.

### Task 5: Home layout and wiring (launcher)

**Files:**
- Modify: `res/layout/activity_main.xml`:
  - a new `weatherPill` TextView where `hijriDate` is now;
  - a new right-hand block (`verseBlock`: `verseRef`, `verseAr`, `verseText`, `prayerHeader`), with
    `prayerChips` moved into it, bottom right, above the dock.
- Modify: `PrayerBar.java`. The Hijri text goes to the `prayerHeader` (`"WAKTU SOLAT · <hijri>"`).
- Modify: `MainActivity.java`:
  - weather refresh inside the existing off-main-thread prayer refresh (when `WeatherStore.due`), then the
    pill is updated on the UI thread;
  - the verse is set on load and on the date change;
  - the place description hides when it would overlap the verse block.
- Bump `versionCode` 18, `versionName '2.4-cuaca'`.

- [ ] Build with `./gradlew testReleaseUnitTest assembleRelease`. The APK must be under 409600 bytes.
- [ ] Render a check: screenshot a layout preview, or run on the emulator if there is one. Otherwise review the
  XML against the spec sketch.
- [ ] Commit.

### Task 6: Phone app settings and bundle

**Files:**
- Modify: `remote/.../Settings.java`. New fields `weather`, `verse` and `lang`, written in the HOME section.
- Modify: `remote/.../MainActivity.java` and `res/layout/activity_main.xml`. Two switches and a Melayu/English
  choice in the Home card, read in `fill()`.
- Modify: `TvVersion.BUNDLED = 18`. Copy the new launcher release APK to `remote/app/src/main/assets/glass-tv.apk`.
- Test: the existing Settings tests get the HOME section keys.

- [ ] Test, implement, build, bump the phone app to 4.7 (versionCode 31), and commit.

### Task 7: Docs and hand-over

- [ ] Update the AGENTS.md "Keep the launcher light" line and the README with the Open-Meteo hosts.
- [ ] Run the full tests for both apps, push `solat` and `solat:main`, and send the phone APK.

# Glass TV Remote (phone app) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android phone app that finds the Mi TV Stick on the home Wi-Fi and changes Glass TV's wallpapers, Home settings, prayer settings, azan MP3s and Bismillah, then restarts Home, without a computer.

**Architecture:** Pure-Java helpers (zones, config merge, subnet maths, TV app list parsing, photo maths, the TV command list) are unit-tested on the JVM. A thin Android layer talks ADB over Wi-Fi with `dev.mobile:dadb` (same protocol as `tools/tv.py`), prepares photos with `Bitmap`, and one plain-views `MainActivity` (no AndroidX) holds the form.

**Tech Stack:** Java 17, Android SDK 34 / minSdk 26, AGP 8.5.2, `dev.mobile:dadb:1.2.10`, JUnit 4, `org.json:json:20240303` (tests).

**Spec:** `docs/solat/design.md`, part "Reka bentuk: Glass TV Remote (telefon)" and the goals list "Glass TV Remote (di telefon)". Mockup (layout sketch) is in the spec.

**Working copy:** `/home/user/cocombee/glass-tv`, branch `solat`, new folder `remote/` (own Gradle project). Ruling: the spec put the code in `/home/user/glass-tv-remote`; it moves into this repo so the owner's one private repo holds everything.

## Global Constraints

- TV package `com.gathin.tvhome`; TV folder `/sdcard/Android/data/com.gathin.tvhome/files/`; files `config.json`, `wallpapers/NN.jpg`, `azan.mp3`, `azan_subuh.mp3`.
- Restart Home: `am force-stop com.gathin.tvhome; am start -n com.gathin.tvhome/.MainActivity`.
- ADB over TCP port **5555**; RSA key generated once into the app's `filesDir` (`adbkey`, `adbkey.pub`).
- Config keys the app writes (exact names): `places`, `wallpaper_seconds`, `dock_apps`, `columns` (4–8, default shown 5), `show_network_pill`, `reduced_motion`, `background_color` (`#RRGGBB`), `prayer_zone`, `prayer_zone_name`, `azan_prayers` (subset of `subuh, zohor, asar, maghrib, isyak`), `azan_text`, `azan_subtext`, `azan_dim` (0/1/2), `bismillah`. Every other key already in `config.json` is kept.
- Photos: centre-crop to 16:9, scale to **1920×1080**, JPEG ≤ **300 KB**, optional darkening ~25 % (on by default), at most **30** photos, names `01.jpg`, `02.jpg`… in app order.
- MP3 ≤ **5 MB** each, copied unchanged.
- Wallpapers are written to `wallpapers.new/` and swapped into `wallpapers/` only after every photo arrived.
- UI copy in Malay. Section order: Sambungan · Gambar latar · Home · Solat · Bismillah · HANTAR KE TV.
- No new listening ports on the phone or the TV.

## Review Focus

- The TV is off, asleep, or debugging is off → a clear Malay message ("TV tidak dijumpai…"), never a crash or an endless spinner (test `tvMessageForRefused`, Task 2).
- `config.json` on the TV is missing, has a Windows BOM, or is not valid JSON → treated as `{}` and rewritten with the app's keys only (tests `missingConfigIsEmpty`, `bomIsStripped`, `brokenConfigIsEmpty`, Task 1).
- Portrait or tiny photos → still centre-cropped to 16:9 without stretching (test `portraitCropsMiddle`, Task 2).
- Phone not on Wi-Fi (mobile data) → no scan of a nonsense subnet; message "Sambung telefon ke Wi-Fi rumah" (test `noWifiNoHosts`, Task 2).
- An item the owner removed after choosing (photo deleted from the gallery) → that photo is skipped and the rest are sent; the result line says how many (covered in Task 3 `Sender` by counting successes; manual check).

---

### Task 1: Project, zones and config merge

**Files:**
- Create: `remote/settings.gradle`, `remote/build.gradle`, `remote/gradle.properties`, `remote/gradlew`, `remote/gradle/wrapper/*` (copied from `launcher/`), `remote/app/build.gradle`, `remote/app/src/main/AndroidManifest.xml` (INTERNET, ACCESS_WIFI_STATE, ACCESS_NETWORK_STATE; `android:usesCleartextTraffic` not needed)
- Create: `remote/app/src/main/resources/zones.json` (copy of `/home/user/glass-tv-remote/ref/zones.json`: 60 entries `[state, code, areas]`)
- Create: `remote/app/src/main/java/my/glasstv/remote/Zones.java`, `ConfigMerge.java`
- Test: `remote/app/src/test/java/my/glasstv/remote/ZonesTest.java`, `ConfigMergeTest.java`

**Interfaces:**
- Produces:
  - `final class Zones { static final class Zone { final String state, code, areas; String label() /* "SGR01 · Gombak, Petaling, …" */ } static List<Zone> all(); static Zone find(String code) /* null if unknown */; static String shortName(Zone z) /* first area: "Gombak" */ }`
  - `final class ConfigMerge { static JSONObject read(@Nullable String text) /* never null; {} for null, blank, BOM-only or broken */; static String merge(@Nullable String existing, Map<String, Object> changes) /* pretty JSON, changes win, a null value removes the key */ }`

- [ ] **Step 1: Write the failing tests**

```java
@Test public void sixtyZones() { assertEquals(60, Zones.all().size()); }
@Test public void shahAlamIsSgr01() { assertTrue(Zones.find("SGR01").areas.contains("S.Alam")); assertEquals("Selangor", Zones.find("SGR01").state); assertNull(Zones.find("XXX99")); }
@Test public void keepsUnknownKeys() { JSONObject o = new JSONObject(ConfigMerge.merge("{\"vpn_app\":\"x\",\"columns\":4}", Map.of("columns", 5)));
  assertEquals("x", o.getString("vpn_app")); assertEquals(5, o.getInt("columns")); }
@Test public void nullRemovesKey() { /* merge {"places":false} with places→null → no "places" key */ }
@Test public void listsBecomeArrays() { /* azan_prayers → List.of("subuh","maghrib") → JSONArray of 2 */ }
@Test public void missingConfigIsEmpty() { assertEquals(0, ConfigMerge.read(null).length()); }
@Test public void bomIsStripped() { assertEquals(4, ConfigMerge.read("﻿{\"columns\":4}").getInt("columns")); }
@Test public void brokenConfigIsEmpty() { assertEquals(0, ConfigMerge.read("{columns:").length()); }
```

- [ ] **Step 2: Run, expect FAIL** — `cd remote && ./gradlew testDebugUnitTest -q` → compilation failure.
- [ ] **Step 3: Implement** both classes (zones loaded once from the classpath resource with `org.json`).
- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** — `git commit -m "Remote: project, JAKIM zones and config merge"`

### Task 2: Pure helpers: subnet, TV apps, photo maths, TV commands, messages

**Files:**
- Create in `remote/app/src/main/java/my/glasstv/remote/`: `Subnet.java`, `TvApps.java`, `PhotoMath.java`, `TvCommands.java`, `Messages.java`
- Test: `SubnetTest.java`, `TvAppsTest.java`, `PhotoMathTest.java`, `TvCommandsTest.java`, `MessagesTest.java`

**Interfaces:**
- Produces:
  - `Subnet { static String ipFromWifiInt(int ip) /* Android little-endian int → "192.168.1.23"; null for 0 */; static List<String> hosts24(@Nullable String self) /* the other 253 addresses of the /24; empty for null */ }`
  - `TvApps { static List<String> parse(String queryActivitiesOutput) /* "pkg/.Act" lines → unique pkgs, ordered, without com.gathin.tvhome */; static String label(String pkg) /* friendly name or the package */ }` — names: YouTube `com.google.android.youtube.tv`, Netflix `com.netflix.ninja`, Disney+ `com.disney.disneyplus`, Prime Video `com.amazon.amazonvideo.livingroom`, Viu `com.vuclip.viu`, iQIYI `com.iqiyi.i18n.tv`, Spotify `com.spotify.tv.android`, Apple TV `com.apple.atve.androidtv.appletv`, Astro GO `com.astro.astro`, unifi TV `com.tm.unifiplay`, Play Store `com.android.vending`, Tonton `my.com.tonton`.
  - `PhotoMath { static int[] cropRect(int w, int h) /* {left, top, width, height} centred 16:9 */; static String fileName(int index) /* 0 → "01.jpg" */; static final int MAX_PHOTOS = 30, MAX_JPEG = 300 * 1024, W = 1920, H = 1080; static final long MAX_MP3 = 5L << 20; }`
  - `TvCommands { static final String DIR = "/sdcard/Android/data/com.gathin.tvhome/files"; static String prepareWallpapers() /* "rm -rf DIR/wallpapers.new; mkdir -p DIR/wallpapers.new" */; static String swapWallpapers() /* "rm -rf DIR/wallpapers; mv DIR/wallpapers.new DIR/wallpapers" */; static String clearWallpapers(); static String restartHome(); static String readConfig() /* "cat DIR/config.json 2>/dev/null" */; static final String LIST_APPS = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER"; }`
  - `Messages { static String forError(Throwable t) }` — Malay: connection refused/timeout → "TV tidak dijumpai. Pastikan TV hidup, sama Wi-Fi, dan USB debugging ON."; auth not granted → "Di TV, tanda Always allow dan tekan OK, kemudian cuba lagi."; anything else → "Gagal: " + message.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void wifiIntToIp() { assertEquals("192.168.1.23", Subnet.ipFromWifiInt(0x1701A8C0)); assertNull(Subnet.ipFromWifiInt(0)); }
@Test public void hostsSkipSelf() { List<String> h = Subnet.hosts24("192.168.1.23"); assertEquals(253, h.size()); assertFalse(h.contains("192.168.1.23")); assertTrue(h.contains("192.168.1.1")); }
@Test public void noWifiNoHosts() { assertTrue(Subnet.hosts24(null).isEmpty()); }
@Test public void parsesTvApps() { assertEquals(List.of("com.netflix.ninja", "com.google.android.youtube.tv"),
  TvApps.parse("com.netflix.ninja/.MainActivity\ncom.gathin.tvhome/.MainActivity\ncom.google.android.youtube.tv/x.Shell\ncom.netflix.ninja/.Other\n")); }
@Test public void friendlyNames() { assertEquals("Netflix", TvApps.label("com.netflix.ninja")); assertEquals("org.foo", TvApps.label("org.foo")); }
@Test public void landscapeCropsSides() { assertArrayEquals(new int[]{240, 0, 3840, 2160}, PhotoMath.cropRect(4320, 2160)); }
@Test public void portraitCropsMiddle() { assertArrayEquals(new int[]{0, 1312, 3000, 1687}, PhotoMath.cropRect(3000, 4312)); }
@Test public void fileNames() { assertEquals("01.jpg", PhotoMath.fileName(0)); assertEquals("30.jpg", PhotoMath.fileName(29)); }
@Test public void swapIsAtomicPair() { assertEquals("rm -rf " + TvCommands.DIR + "/wallpapers; mv " + TvCommands.DIR + "/wallpapers.new " + TvCommands.DIR + "/wallpapers", TvCommands.swapWallpapers()); }
@Test public void tvMessageForRefused() { assertTrue(Messages.forError(new java.net.ConnectException("refused")).startsWith("TV tidak dijumpai")); }
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testDebugUnitTest -q`.
- [ ] **Step 3: Implement** the five classes. Crop: wider than 16:9 → full height, width = h·16/9; otherwise full width, height = w·9/16; centred; integer division (rounds down).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `git commit -m "Remote: subnet, TV apps, photo maths, TV commands, messages"`

### Task 3: Talking to the TV

**Files:**
- Create: `remote/app/src/main/java/my/glasstv/remote/Tv.java`, `Discovery.java`, `PhotoPrep.java`, `Sender.java`, `Settings.java`
- Test: `remote/app/src/test/java/my/glasstv/remote/SettingsTest.java`

**Interfaces:**
- Consumes: Task 1 `ConfigMerge`, Task 2 `TvCommands`, `PhotoMath`, `Messages`
- Produces:
  - `final class Tv implements Closeable { static Tv connect(Context c, String host) throws IOException /* dadb, port 5555, key in filesDir, 15 s connect timeout */; String shell(String cmd) throws IOException /* stdout */; void push(File f, String remotePath) throws IOException; }`
  - `final class Discovery { static List<String> scan(Context c) /* hosts with TCP 5555 open on the phone's /24, 64 threads, 400 ms timeout, sorted */ }`
  - `final class PhotoPrep { static File prepare(Context c, Uri uri, boolean darken, int index) throws IOException /* honours EXIF orientation via ExifInterface from android.media, cropRect, 1920×1080, JPEG quality from 90 down by 5 until ≤ MAX_JPEG */ }`
  - `final class Settings { /* plain fields the form fills */ Boolean places; Integer wallpaperSeconds; List<String> dockApps; Integer columns; Boolean networkPill, reducedMotion, bismillah; String backgroundColor; String zone, zoneName; List<String> azanPrayers; String azanText, azanSubtext; Integer azanDim; Map<String, Object> changes() /* only fields that are non-null, under the exact config keys; places true also sets wallpaper_seconds → null */ }`
  - `final class Sender { interface Progress { void step(String malayText); } static String send(Context c, String host, Settings s, List<Uri> photos, boolean darken, @Nullable Uri azan, @Nullable Uri azanSubuh, Progress p) /* returns the final Malay result line, e.g. "Siap ✓ 12 gambar dihantar" */ }`

- [ ] **Step 1: Write the failing test**

```java
@Test public void onlySetFieldsAreChanges() { Settings s = new Settings(); s.columns = 5; s.zone = "SGR01";
  assertEquals(Map.of("columns", 5, "prayer_zone", "SGR01"), s.changes()); }
@Test public void ownPicturesTurnPlacesOff() { Settings s = new Settings(); s.places = false; s.wallpaperSeconds = 30;
  Map<String,Object> c = s.changes(); assertEquals(false, c.get("places")); assertEquals(30, c.get("wallpaper_seconds")); }
@Test public void placesClearsTheInterval() { Settings s = new Settings(); s.places = true; assertTrue(s.changes().containsKey("wallpaper_seconds")); assertNull(s.changes().get("wallpaper_seconds")); }
```

- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** `Settings`, then `Tv`, `Discovery`, `PhotoPrep`, `Sender`. `Sender.send` order: connect → read config → for own photos: prepare each (skip failures, count), `prepareWallpapers`, push each to `DIR/wallpapers.new/NN.jpg`, `swapWallpapers`; for places: `clearWallpapers`; MP3s: size check then push to `DIR/azan.mp3` / `DIR/azan_subuh.mp3` (copy the `Uri` to a cache file first); write merged config to a cache file and push to `DIR/config.json`; `restartHome`; any exception → `Messages.forError`.
- [ ] **Step 4: Run tests and build** — `./gradlew testDebugUnitTest assembleDebug -q` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Remote: ADB connection, discovery, photo preparation and sending"`

### Task 4: The screen, the APK and the guide

**Files:**
- Create: `remote/app/src/main/java/my/glasstv/remote/MainActivity.java`, `remote/app/src/main/res/layout/activity_main.xml`, `res/values/strings.xml` (app name "Glass TV Remote"), `res/values/styles.xml` (dark Material theme `android:Theme.Material.NoActionBar`, gold accent `#E3C78C`), launcher icon (`res/drawable/ic_launcher.xml`, vector: gold mosque-free simple "G" glass circle)
- Create: `docs/solat/REMOTE.md` (Malay install-and-use guide)

**Interfaces:**
- Consumes: Task 3 `Discovery.scan`, `Tv.connect`, `Sender.send`, `Settings`; Task 2 `TvApps.parse/label`, `TvCommands.LIST_APPS`; Task 1 `Zones.all/find/shortName`.

- [ ] **Step 1: Layout** — one `ScrollView`, sections in the constrained order: Sambungan (status text, "Cari TV" button, IP `EditText`, "Sambung" button); Gambar latar (RadioGroup "Tempat menarik"/"Gambar saya", "+ Pilih foto" button using `ACTION_OPEN_DOCUMENT` `image/*` with `EXTRA_ALLOW_MULTIPLE`, a horizontal strip of thumbnails—long-press removes—, "Gelapkan sedikit" Switch on, interval Spinner: 5 s, 15 s, 30 s, 1 min, 5 min, 30 min, 1 jam, 1 hari); Home ("Pilih app dock" button opening a multi-choice dialog of `TvApps` after connecting, max 5, order = tap order; columns Spinner 4–8 default 5; Switches "Butang Wi-Fi", "Animasi ringan"); Solat (zone Spinner of `Zone.label()` grouped by state, default SGR01; 5 CheckBoxes; "Pilih MP3 azan" and "Pilih MP3 azan Subuh" via `ACTION_OPEN_DOCUMENT` `audio/*`; text fields for ayat utama and ayat kecil prefilled with the launcher defaults; dim Spinner Ringan/Sederhana/Gelap); Bismillah (Switch on); big "HANTAR KE TV" button; a progress/result `TextView`.
- [ ] **Step 2: Behaviour** — last IP in `SharedPreferences`; network work on a single background `Executor`, UI updates via `runOnUiThread`; "HANTAR" disabled while sending; `prayer_zone_name` = `Zones.shortName(zone)`, except SGR01 → "Shah Alam" (ruling: the owner's town).
- [ ] **Step 3: Build** — `./gradlew testDebugUnitTest assembleDebug -q`; copy `app/build/outputs/apk/debug/app-debug.apk` to `/home/user/cocombee/glass-tv/glass-tv-remote.apk`.
- [ ] **Step 4: Write `docs/solat/REMOTE.md`** — install APK (allow "Install unknown apps"), first connect (Always allow on TV), each section, what "HANTAR" does, troubleshooting (TV not found; Always allow; photos too many).
- [ ] **Step 5: Commit** — `git commit -m "Remote: the screen, debug APK and Malay guide"`

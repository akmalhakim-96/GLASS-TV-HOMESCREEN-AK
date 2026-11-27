# Weather, air quality and a daily Quran verse on Home: design

Status: draft for the owner's approval. Date: 2026-11-27.

## Intent

The owner showed a photo of the Home screen they want. It has two things Glass TV lacks:

- a weather line under the clock: temperature, condition and an air-quality reading;
- a Quran verse of the day, with the verse reference, Arabic text and a translation, placed above the prayer
  times on the right.

The screens are watched by families at home and by pupils and teachers at school, on about 20 TVs. The
verse must be suitable for both audiences, and its text must be exact. The owner has said several times that
Home must stay light.

## Goals

1. A weather pill under the clock, for example `⛅ 29° Berawan · Udara 50 Sederhana`. It refreshes about
   hourly.
2. A daily verse block on the right, as in the photo:
   - the reference (`AT-TALAQ 65:3`);
   - the Arabic text, right-aligned;
   - the translation.
3. The layout of the photo:
   - the prayer times move to the bottom right, under the verse;
   - their header becomes `WAKTU SOLAT · 11 JAMADILAKHIR 1448H`;
   - the Hijri date leaves the clock area, and the weather pill takes its place;
   - the place title and description stay at the bottom left.
4. Phone app settings, per TV, in the Home section:
   - Cuaca & kualiti udara, on/off;
   - Ayat al-Quran harian, on/off;
   - Bahasa paparan, Melayu/English. This sets the verse translation and the weather words.

## Non-goals

- Weather forecasts, rain alerts or hourly charts.
- The official APIMS index. No stable open source exists for it.
- Showing any verse of the Quran. Only a curated set is shown.
- Audio, tafsir, or scrolling long verses.

## Anti-goals

These would make the feature a failure even if it technically works:

- **Wrong Quran text.** Not one letter may be typed from memory. All text comes from a published source.
- **An empty or broken pill when offline or when the source fails.** The pill shows the last good reading
  for up to 3 hours, then hides. It never shows "error" on screen.
- **A heavier Home:**
  - no new background service;
  - no network use while another app is in front;
  - the launcher APK stays under its 400 KB CI limit (it is 342 KB now).
- **Calling the air-quality number "API".** The number is not Malaysia's official APIMS reading, so the label
  says `Udara` / `Air`.

## Approaches considered

- **A. Weather and air quality from Open-Meteo; verses bundled in the app.** Chosen by the owner.
  - Open-Meteo is free, needs no key or account, and gives current conditions.
  - The bundled verses work offline and are curated.
- **B. MET Malaysia and APIMS.** Rejected.
  - MET Malaysia needs a registered token and gives forecasts only, not current temperature.
  - APIMS would have to be scraped. A scraper breaks silently, on all 20 TVs at once.
- **C. Like A, but a random verse fetched online each day.** Rejected.
  - It needs internet.
  - A random verse can be unsuitable out of context, which matters at school.

## Design

### Data: weather (launcher)

- **Location.**
  - The TV already knows its JAKIM zone (`prayer_zone`). A table in the launcher maps each zone to the
    coordinates of its main town (SGR01 → Shah Alam, 3.07, 101.52).
  - No new setting is needed, and older phone apps keep working.
  - With no zone, or a zone missing from the table, there is no weather.
- **Requests.** Two plain HTTPS GETs, made off the main thread:
  - `https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&current=temperature_2m,weather_code,is_day&timezone=Asia/Kuala_Lumpur`
  - `https://air-quality-api.open-meteo.com/v1/air-quality?latitude=..&longitude=..&current=us_aqi`
- **When it fetches.**
  - Only when Home is shown (`onResume`, and the existing minute tick while Home is in front).
  - Only if the last good reading is older than 60 minutes.
  - Never while another app is in front. This matches how the prayer times refresh.
- **Cache.**
  - The last good reading and its time are kept in SharedPreferences.
  - The pill shows it while it is younger than 3 hours, and hides after that.
  - A failed or unparsable reply changes nothing. If only the air-quality request fails, the pill shows the
    weather alone.
- **Condition words.** WMO `weather_code` maps to a few words and an emoji, in Malay or English:

  | Code | Melayu | English |
  |---|---|---|
  | 0 | Cerah | Clear |
  | 1–3 | Berawan | Partly cloudy / Cloudy |
  | 45/48 | Berkabus | Fog |
  | 51–67, 80–82 | Hujan | Rain |
  | 95–99 | Ribut petir | Thunderstorm |

  At night (`is_day=0`) the icon changes, for example 🌙 for clear.
- **Air-quality bands.** The bands use Malaysia's API wording, applied to the US AQI number. Each band has a
  colour, and the photo shows the number in green.

  | Band | Melayu | Colour |
  |---|---|---|
  | 0–50 | Baik | green |
  | 51–100 | Sederhana | yellow |
  | 101–200 | Tidak sihat | orange |
  | 201–300 | Sangat tidak sihat | red |
  | 301+ | Berbahaya | purple |

  English uses the same bands: Good, Moderate, Unhealthy, Very unhealthy, Hazardous.

### Data: verses (bundled)

- **The set.** About 60 verses, kept short so the Arabic fits on 2 lines at most. They are self-contained and
  suitable for a family room and a classroom. Themes:
  - trust in Allah (tawakkal);
  - patience;
  - knowledge and learning;
  - gratitude;
  - kindness to parents and others;
  - prayer;
  - hope;
  - duas from the Quran.
- **The source: Quran.com API v4, fetched once at build time.**
  - The Arabic is `text_imlaei`: standard script with full harakat. Android TV's own Arabic font renders it
    correctly. The Uthmani script uses marks some TV fonts show as boxes, and bundling a Quran font would
    break the 400 KB limit.
  - The Malay translation is Abdullah Muhammad Basmeih (Quran.com translation 39).
  - The English translation is Sahih International (translation 20).
  - Footnote markers are stripped.
- **The file.** A script, `tools/ayat/fetch.py`, writes `launcher/app/src/main/res/raw/ayat.json`. The script
  and the verse list are committed, so the file can be regenerated and checked.
- **Choosing the verse.**
  - The day's verse is `dayOfYear % count`, in Malaysia time.
  - Every TV shows the same verse on the same day, which is useful at school.
  - It changes at midnight.

### Layout (launcher)

The layout follows the owner's photo. The verse text in this sketch is only an illustration; the real text
comes from `ayat.json`.

```
 8:59 PTG                                   [Inputs] [Wi-Fi] [⚙]
 21 NOV 2026
 (⛅ 29° Berawan · Udara 50 Sederhana)

                                                AT-TALAQ 65:3
                                    وَمَنْ يَتَوَكَّلْ عَلَى اللَّهِ فَهُوَ حَسْبُهُ
                  Dan sesiapa yang bertawakal kepada Allah, maka Allah cukuplah baginya.
 Tokyo Cyberpunk Alley              WAKTU SOLAT · 11 JAMADILAKHIR 1448H
 CITYSCAPE                         Subuh Syuruk Zohor  Asar  Maghrib Isyak
 Moody neon reflections…           05:50 06:58 13:02 16:24 19:00  20:13
 [ Netflix ][ YouTube ][ … dock … ]
```

- **The weather pill** is a dark glass pill (the photo's style), placed where the Hijri line is now.
  - The air-quality part is coloured by its band.
  - It is hidden when weather is off or there is no fresh reading.
- **The verse block** is a right-aligned column above the prayer block:
  - the reference: small caps, letter-spaced;
  - the Arabic: about 22sp, maximum 2 lines;
  - the translation: about 14sp, maximum 2 lines, ellipsized.
  - It is hidden when the verse is off.
- **The prayer block** (`prayerChips`) moves from under the place text to the bottom right, under the verse.
  - The Hijri date moves into its header.
  - The next prayer keeps its highlight. The photo shows green. Today it is gold, and stays gold unless the
    owner asks otherwise.
- **With prayer times off**, the verse sits alone at the bottom right. With both off, the right side is empty,
  as now.
- **The next-prayer pill** at the top right (an existing feature) stays as it is.
- **Small screens.** On 720p screens all of this must still fit above the dock.
  - If the place description and the verse would overlap, the description hides first.
  - The place title is never hidden.
- **Reduced motion** (an existing setting): nothing here animates except a fade when the verse changes.

### Config keys (config.json)

| Key | Type | Default when missing |
|---|---|---|
| `weather` | bool | `true` when a known zone is set, otherwise no weather |
| `daily_verse` | bool | `true` |
| `display_language` | `"ms"` / `"en"` | `"ms"` |

TVs updated without a new send from the phone therefore show the verse at once. They also show the weather
if their zone is known.

### Phone app

- The Home section gets two switches and a language choice, read from and written to the keys above with the
  existing merge: only this section changes.
- The new launcher version is bundled (`TvVersion.BUNDLED` = the new versionCode), so `Pasang` updates the
  TVs.

## Constraints

- **Launcher rules** (AGENTS.md):
  - stays light;
  - no network while another app is in front;
  - decoding off the main thread;
  - APK under 400 KB.
- **AGENTS.md wording.** The rule "no network use but the weekly places pictures" is already extended by the
  JAKIM fetch. It is extended again for Open-Meteo: 2 hosts, about 2 small requests an hour while Home is
  shown. The AGENTS.md wording and the README are updated to say so.
- **Build environment.** It needs network access to `api.quran.com` once, to generate `ayat.json`. The owner
  is asked for that host when the plan reaches it. Open-Meteo is needed only on the TVs.
- **Commits.** Human identity only, as AGENTS.md says.

## Left to the builder

- The exact zone → coordinates table. Use the main town of each JAKIM zone; ±10 km is fine for weather.
- The exact list of 60 verses. Apply the themes and the length limit above, and give the list to the owner to
  look over before it is bundled.
- Pixel values, as long as the result matches the photo's proportions at 1080p and fits at 720p.
- Tests:
  - pure parsing of both Open-Meteo replies;
  - weather code → words;
  - AQI → band;
  - day → verse index;
  - zone → coordinates;
  - the stale rule (3 hours);
  - the config defaults.

## Calls made without the owner (check these)

- Air quality is labelled `Udara` / `Air`, not `API`.
- The location comes from the prayer zone, with no separate setting.
- There are 60 curated short verses, one per day, the same on every TV.
- The Arabic is in the imlaei script, not Uthmani, for font safety.
- The next-prayer highlight stays gold, not the photo's green.
- The language choice also sets the weather words.
- The verse and weather are on by default after the update.

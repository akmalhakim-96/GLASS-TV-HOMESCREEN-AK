# Glass TV Solat + Glass TV Remote — reka bentuk

Dua bahagian untuk Mi TV Stick pemilik (MiTV-AESP0, Android TV 9, 1 GB RAM):

1. **Glass TV Solat**: salinan Glass TV (launcher) yang ditambah waktu solat JAKIM di Home dan azan
   dengan "pulau" kaca semasa menonton.
2. **Glass TV Remote**: app telefon Android untuk menukar gambar latar, tetapan Home dan tetapan solat
   terus dari telefon, pada Wi-Fi yang sama.

Mockup yang diluluskan: `mockup-home-5app.png` (Home) dan `mockup-azan-tengah.png` (azan).

## Kenapa

- Pemilik mahu peringatan waktu solat yang jelas di TV keluarga, dengan azan, tanpa merosakkan
  pengalaman menonton: video terus berjalan, bunyi dikecilkan, dan boleh diteruskan dengan satu butang.
- Pemilik mahu menukar gambar latar sendiri dan semua tetapan dari telefon. Telefon sentiasa ada di
  tangan; komputer tidak.

## Matlamat

### Glass TV Solat (di TV)
- **Home**: pil waktu solat seterusnya di atas kanan dengan kiraan ("Zohor 1:10 PM · 7j 2m lagi");
  tarikh Hijri di bawah tarikh biasa; baris 5 waktu (Subuh, Zohor, Asar, Maghrib, Isyak) di bawah kapsyen
  tempat, dengan waktu seterusnya diserlah emas.
- **Data**: JAKIM e-solat mengikut zon (contoh WLY01), diambil sebulan sekali dan disimpan supaya
  berfungsi tanpa internet sehingga data habis.
- **Azan pada waktu solat** (5 waktu; setiap satu boleh dihidup/matikan):
  - MP3 azan pemilik (azan biasa; azan Subuh berasingan jika diberi).
  - Bunyi app lain dikecilkan semasa azan.
  - Pulau kaca di atas: ikon, "Azan Maghrib · 7:12 PM", zon, gelombang bunyi, "OK · teruskan".
  - Lapisan gelap lutsinar di seluruh skrin + ayat di tengah (boleh ditetapkan; `{waktu}` dan `{zon}`
    diganti) + ayat kecil.
  - Azan tamat atau OK ditekan: semua hilang, bunyi kembali normal.
- **Remote**: semua butang berfungsi seperti biasa (Home, butang Netflix/YouTube, volume, anak panah,
  Back). Hanya tekanan OK pertama semasa azan dipinjam untuk menghentikan azan.
- **Dock 5 app** melalui tetapan sedia ada `"columns": 5` (tiada perubahan kod).

### Glass TV Remote (di telefon)
- Cari stick sendiri dalam Wi-Fi (imbasan port 5555 pada subnet /24), dengan pilihan taip IP; ingat
  stick terakhir.
- Gambar latar: "Tempat menarik" atau "Gambar saya" (pilih foto dari galeri, had 30, kecil dan potong
  automatik ke 1920×1080 ≤300 KB, gelapkan ±25% secara default dengan suis), dan berapa kerap bertukar.
- Home: 5 app dock dan susunannya, app sebaris (4–8, default 5), butang Wi-Fi, animasi ringan,
  warna latar.
- Solat: pilih zon JAKIM (senarai penuh), MP3 azan biasa + Subuh, azan on/off setiap waktu, ayat utama,
  ayat kecil, tahap gelap (ringan/sederhana/gelap).
- Satu butang **HANTAR KE TV**.

## Bukan matlamat
- Tiada blur video app lain (mustahil pada Android 9 dan app berbayar menghalang); lapisan gelap
  digunakan.
- Tiada link YouTube untuk azan (melanggar syarat YouTube dan bercanggah dengan "video terus berjalan").
- Tiada azan bila TV dimatikan; Glass TV tidak menghidupkan TV.
- Tiada iPhone, tiada Play Store, tiada push ke GitHub `cocombee/glass-tv`.
- Tiada imsak/syuruk sebagai azan (syuruk boleh dipapar kemudian jika diminta).

## Anti-matlamat
- Butang remote lain tersekat atau berkelakuan pelik semasa azan.
- Waktu solat salah (zon salah, zon waktu salah, data lama dipapar seolah-olah terkini tanpa tanda).
- Azan berbunyi dua kali, atau tidak berbunyi selepas stick dihidupkan semula.
- Home jadi lembap pada stick 1 GB, atau APK launcher melebihi 400 KB.
- Membuka port rangkaian baharu pada TV.
- Merosakkan tetapan sedia ada dalam `config.json`.

## Kekangan
- Launcher membaca `/sdcard/Android/data/com.gathin.tvhome/files/config.json` (overlay atas `config.xml`;
  kunci tidak dikenali diabaikan) dan dimulakan semula dengan `am force-stop` + `am start`.
- Stick: debug rangkaian TCP 5555 (kunci RSA, "Always allow" sekali). USB debugging kekal ON.
- `HomeKeyService` (perkhidmatan aksesibiliti, `canRequestFilterKeyEvents`) sudah dihidupkan pada stick
  ini melalui `-HomeKeyService`.
- Overlay di atas app lain perlukan `SYSTEM_ALERT_WINDOW`, diberi melalui
  `appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow` semasa pasang.
- Android 9: perkhidmatan latar perlu foreground service untuk bermain audio.
- Sesi cloud tidak dapat mencapai `www.e-solat.gov.my` buat masa ini; format API mesti disahkan (minta
  hos dibenarkan) sebelum parser ditulis.

## Pendekatan
- **Solat dalam Glass TV sendiri (DIPILIH)**: satu app di TV, guna semula `HomeKeyService` untuk OK.
  Kos: Glass TV "bangun" 5 kali sehari walaupun app lain di depan (menyimpang daripada prinsip projek
  asal; diterima untuk salinan pemilik).
- App azan berasingan di TV: ditolak. Dua app, dua tempat tetapan, data dikongsi atau diambil dua kali.
- Telefon melalui debug Wi-Fi (DIPILIH) berbanding halaman web dihoskan TV (ditolak: port baharu,
  kurang selamat) atau app sedia ada (ditolak: terlalu banyak langkah).

## Reka bentuk: Glass TV Solat

### Tetapan baharu dalam `config.json`
| Kunci | Jenis | Default |
|---|---|---|
| `prayer_zone` | kod zon JAKIM, contoh `"WLY01"` | tiada → ciri solat mati |
| `prayer_zone_name` | nama untuk dipapar, contoh `"Kuala Lumpur"` | kod zon |
| `azan_prayers` | senarai: `subuh`,`zohor`,`asar`,`maghrib`,`isyak` | kelima-lima |
| `azan_text` | ayat utama dengan `{waktu}`, `{zon}` | "Telah masuk waktu solat fardu {waktu} bagi kawasan {zon} dan sekitarnya" |
| `azan_subtext` | ayat kecil | "Marilah menunaikan solat" |
| `azan_dim` | 0 ringan, 1 sederhana, 2 gelap | 1 |

Fail: `azan.mp3`, `azan_subuh.mp3` (pilihan; jika tiada, guna `azan.mp3`). Tiada MP3 → pulau dan ayat
tetap dipapar tanpa bunyi.

### Data JAKIM
- Ambil jadual sebulan untuk zon; simpan JSON dalam storan app. Ambil semula bila zon bertukar, bila
  bulan bertukar, atau bila tinggal <7 hari data; cuba semula dengan jarak masa jika gagal.
- Zon waktu: Asia/Kuala_Lumpur untuk semua kiraan, bukan zon waktu stick.
- Data tiada untuk hari ini → baris waktu disembunyikan dan tiada azan dijadualkan (tiada waktu
  palsu).

### Penjadualan
- `AlarmManager.setExactAndAllowWhileIdle` untuk waktu solat seterusnya sahaja; selepas berbunyi,
  jadualkan yang berikutnya.
- Jadualkan semula selepas stick dihidupkan (`BOOT_COMPLETED`), bila masa atau zon waktu berubah, dan bila
  `config.json` dibaca semula.
- Abaikan alarm yang tiba >2 minit lewat (contohnya stick baru bangun), untuk elak azan lewat.

### Azan
- Alarm → foreground service: MediaPlayer, `AudioAttributes` USAGE_MEDIA, minta audio focus
  `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (app lain dikecilkan; sesetengah app seperti Netflix mungkin pause).
- Overlay `TYPE_APPLICATION_OVERLAY` dengan `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`, supaya semua butang
  terus ke app di depan. Isi: lapisan gelap (alpha ikut `azan_dim`), ayat tengah, pulau kaca di atas
  (gaya `GlassDrawable` sedia ada).
- `HomeKeyService.onKeyEvent`: jika azan aktif dan butang ialah DPAD_CENTER/ENTER, telan keydown dan
  keyup pertama, hentikan azan. Semua butang lain diteruskan seperti sekarang.
- Tamat: lepaskan audio focus, buang overlay, hentikan service.
- Jika `SYSTEM_ALERT_WINDOW` tiada, azan tetap berbunyi tanpa overlay.

### Home
- Pil waktu seterusnya mengambil tempat butang Inputs bila Inputs disembunyikan (seperti pada stick),
  dan duduk di sebelah kirinya jika Inputs dipapar. Pil bukan butang (tidak boleh difokus).
- Baris 5 cip waktu di bawah kapsyen tempat; cip seterusnya diserlah emas; dikemas kini setiap minit
  bersama jam.
- Tarikh Hijri daripada data JAKIM, di bawah tarikh biasa.

### Pemasangan
- `install-prebuilt.ps1` tambah: `appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow`.
- APK baharu menggantikan yang lama (tandatangan sama), tetapan sedia ada kekal.

## Reka bentuk: Glass TV Remote (telefon)
- Pustaka `dev.mobile:dadb` untuk ADB; kunci RSA dijana sekali dan disimpan dalam app.
- Satu skrin, bahasa Melayu, bahagian: Sambungan · Gambar latar · Home · Solat · HANTAR KE TV.
- Hantar: baca `config.json` semasa → gabung kunci yang diubah sahaja → tulis. Foto ditulis ke
  `wallpapers.new/`, kemudian ditukar ke `wallpapers/` (`01.jpg`, `02.jpg`… ikut susunan).
  MP3 disalin tanpa ubah (had 5 MB setiap satu). Mulakan semula Home. Papar "Siap ✓" atau ralat mudah.
- Dock: senarai app TV daripada `cmd package query-activities … LEANBACK_LAUNCHER`, nama mesra untuk
  app biasa, pilih hingga 5 dan susun.
- Zon JAKIM: senarai statik penuh dalam app, dikumpul ikut negeri.

## Ujian
- Ujian unit: parser JAKIM, pemilihan waktu seterusnya (termasuk selepas Isyak → Subuh esok),
  penggantian `{waktu}`/`{zon}`, penggabungan `config.json`, penamaan fail, pengecilan foto.
- Tiada stick atau emulator di cloud: ujian sebenar dibuat oleh pemilik pada stick, mengikut senarai
  semak (Home, azan sambil YouTube, OK, Home semasa azan, volume, restart stick).

## Susunan kerja
1. Glass TV Solat (APK launcher baharu + skrip pasang dikemas kini). Pemilik boleh uji dengan tema
   `config.json` yang saya sediakan sebelum app telefon siap.
2. Glass TV Remote (APK telefon).

## Diserahkan kepada builder
- Kotlin atau Java untuk app telefon (launcher kekal Java), rupa UI Material 3, cara imbasan.
- Animasi pulau masuk/keluar (ikut gaya glide Glass TV).
- Teks ralat yang tepat.

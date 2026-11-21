# Glass TV Remote: app telefon

Tukar gambar latar, tetapan Home, waktu solat, azan dan Bismillah terus dari telefon Android.
Telefon dan Mi TV Stick mesti guna **Wi-Fi yang sama**. Komputer tidak diperlukan.

## Pasang (sekali sahaja)

1. Hantar fail `glass-tv-remote.apk` ke telefon, contohnya melalui WhatsApp kepada diri sendiri atau Google Drive.
2. Buka fail itu di telefon. Jika telefon bertanya, benarkan **Pasang app tidak diketahui** (Install unknown apps) untuk app yang anda guna untuk membuka fail itu.
3. Tekan **Pasang**. App **Glass TV Remote** muncul dengan ikon TV emas.

**Syarat di Mi TV Stick:** **USB debugging** mesti hidup (Settings → Device Preferences → Developer options). Ia memang sudah hidup jika anda pernah pasang Glass TV.

## Jenis TV

Pilih jenis TV di bahagian atas app. Selepas bersambung, app mengesan jenis TV sendiri.

| Jenis | Glass TV |
|---|---|
| Android TV, Google TV, Skyworth, Mi TV Stick / Mi Box | ✅ Boleh |
| Samsung (Tizen), LG (webOS) | ❌ Tidak boleh: TV ini tidak menjalankan app Android dan Home-nya tidak boleh ditukar. Pasang stick Google TV atau Mi TV Stick di HDMI, hidupkan HDMI-CEC, kemudian pilih jenis stick itu. |

## Pasang Glass TV di TV tanpa komputer

1. Di Mi TV Stick: **Settings → Device Preferences → About**, tekan OK pada **Build** sebanyak 7 kali.
   Kemudian buka **Developer options** dan hidupkan **USB debugging**.
2. Di telefon: buka Glass TV Remote dan tekan **Cari TV** (atau taip IP TV).
3. Tekan **Pasang & jadikan Home**, kemudian **Pasang**.
   Di TV, tanda **Always allow** dan tekan **OK** bila ditanya.
4. Tunggu ±1 minit. Bila keluar **Siap ✓ Glass TV kini Home**:
   - pilih zon dan MP3 azan dalam bahagian **Solat**;
   - kemudian tekan **HANTAR KE TV**.

Butang ini sama dengan skrip komputer:
- Glass TV dipasang dan diuji dahulu, sebelum Home asal disentuh.
- Home asal **dimatikan, bukan dibuang**.
- Jika ada yang tidak kena, Home asal dikembalikan dengan sendiri.
- Butang ini juga memberi kebenaran pulau azan, dan menghidupkan butang Home dan OK pada remote.

Buang iklan (debloat) masih perlu komputer. Ia pilihan sahaja.

## Kali pertama sambung

1. Buka app dan tekan **Cari TV**. App mencari stick dalam Wi-Fi rumah dengan sendiri.
   Jika tidak dijumpai, taip IP stick (Settings → Network & Internet di TV), kemudian tekan **Sambung**.
2. Di TV akan keluar **"Allow USB debugging?"**. Tanda ✅ **Always allow from this computer** dan tekan **OK**.
   Anda ada 60 saat untuk berbuat demikian. Selepas itu TV tidak akan bertanya lagi untuk telefon ini.
3. Bila tertulis **Bersambung ✓**, borang dalam app diisi dengan tetapan yang sedang digunakan di TV.

## Bahagian dalam app

| Bahagian | Apa boleh diubah |
|---|---|
| **Gambar latar** | *Tempat menarik* (bertukar setiap minggu), atau *Gambar saya*: pilih sehingga 30 foto. Tekan lama pada foto untuk membuangnya. *Gelapkan sedikit* supaya jam mudah dibaca. *Tukar setiap* 5 saat hingga 1 hari. |
| **Home** | *Pilih app dock*: tanda sehingga 5 app, dan susunan tekan menjadi susunan dock. *App sebaris* (4 hingga 8). Butang Wi-Fi. Animasi ringan. |
| **Solat** | Zon JAKIM (60 zon, default SGR01 Shah Alam). Azan untuk setiap waktu. MP3 azan biasa dan Subuh (maksimum 5 MB setiap satu). Ayat di tengah skrin (`{waktu}` dan `{zon}` diisi sendiri). Tahap gelap latar semasa azan. |
| **Bismillah** | Hidup atau matikan Bismillah semasa TV dihidupkan. |

Tekan **HANTAR KE TV**. App akan:
1. Menyediakan foto: dipotong 16:9, saiz 1920×1080, bawah 300 KB.
2. Menghantar foto dan fail MP3.
3. Menyimpan tetapan. Tetapan lain di TV yang tidak ada dalam app kekal.
4. Memulakan semula Home.

Bila keluar **Siap ✓**, semuanya sudah di TV.

Foto hanya disimpan dalam telefon dan stick anda. Tiada apa yang dihantar ke internet.

## Jika ada masalah

| Mesej | Apa perlu dibuat |
|---|---|
| *TV tidak dijumpai…* | Pastikan TV hidup (bukan tidur), telefon dan TV guna Wi-Fi yang sama, dan USB debugging hidup. Cuba taip IP sendiri. |
| *Di TV, tanda Always allow…* | Pergi ke TV, tanda Always allow dan tekan OK, kemudian tekan **Sambung** sekali lagi. |
| *Sambung telefon ke Wi-Fi rumah dahulu* | Telefon sedang guna data mudah alih. Tukar ke Wi-Fi. |
| *… melebihi 5 MB* | Pilih fail MP3 azan yang lebih kecil. |
| *(n gambar tidak dapat dibaca)* | Foto itu sudah dipadam dari galeri atau bukan gambar. Foto lain tetap dihantar. |

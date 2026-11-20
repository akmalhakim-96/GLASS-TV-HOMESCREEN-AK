Tema Glass TV Solat untuk Shah Alam (zon JAKIM SGR01).

Sebelum hantar tema ini ke TV, letak fail azan anda dalam folder ini:
  azan.mp3        azan untuk Zohor, Asar, Maghrib dan Isyak (dan Subuh jika tiada fail di bawah)
  azan_subuh.mp3  (pilihan) azan Subuh

Kemudian, dari folder C:\glass-tv:
  powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost <IP stick> themes\solat-shah-alam

Ayat di tengah skrin boleh ditukar dengan menambah dalam config.json, contohnya:
  "azan_text": "Telah masuk waktu solat fardu {waktu} bagi kawasan {zon} dan sekitarnya",
  "azan_subtext": "Marilah menunaikan solat",
  "azan_dim": 1
{waktu} dan {zon} diganti secara automatik. azan_dim: 0 ringan, 1 sederhana, 2 gelap.
Untuk azan pada waktu tertentu sahaja: "azan_prayers": ["subuh", "maghrib"]

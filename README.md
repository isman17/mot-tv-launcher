# Mot TV Launcher

Launcher Android TV custom yang ringan untuk TCL Google TV (32S51K, Android 14) — dibuat dari nol menggantikan home bawaan yang berat dan penuh iklan.

## Fitur

- **Beranda** — 7 tile (Browser, YouTube, Netflix, WeTV, Vidio, Games, Play Store) memakai banner/ikon asli tiap aplikasi, hero mengikuti fokus, quote tokoh bangsa yang berganti tiap hari (pool 123 quote terverifikasi di `quotes/`), jam kecil di pojok, indikator Wi-Fi, tombol **Semua App** di samping quote
- **Semua App** — grid semua aplikasi terinstal (A–Z), klik langsung buka
- **Games** — folder berisi game yang terinstal, otomatis terisi
- **Pencarian + Hasil + Pemutar Video** — browser bawaan (WebView), hasil pencarian web (DuckDuckGo HTML), pemutar Media3 ExoPlayer untuk video web (konten DRM tetap di aplikasi resminya)
- **Jadwal Bola** — sumber hybrid: ESPN (tanpa kunci) untuk Premier League/La Liga/Serie A/Bundesliga/Champions League, TheSportsDB khusus Liga Indonesia (diprioritaskan, grup pertama); logo klub & liga asli, jam WIB, kalender pilih tanggal bebas, pill platform streaming (mapping manual musim 2025/26)
- **Jadwal Sholat** — data Kemenag via API MyQuran (518 kota), lokasi searchable lewat tombol "Ganti Kota", default Bandung, hitung mundur sholat berikutnya
- **Pengaturan** — ganti background (foto bawaan atau gambar sendiri dari penyimpanan TV), slider kegelapan, pintasan ke Pengaturan Android TV

## Struktur

- `app-project/` — project Android (Kotlin, minSdk 26, targetSdk 34)
- `quotes/` — pool quote tokoh bangsa terverifikasi + catatan sumbernya
- `design/` — mockup design versi release (9 frame)
- `backgrounds/`, `logo/` — aset gambar bawaan
- `BUILD-REPORT.md` — laporan build per fase (status real vs placeholder dicatat jujur)

## Build

Butuh JDK 17 + Android SDK (platform 34, build-tools 34) + Gradle 8.9:

```
cd app-project
gradle assembleDebug
```

Penandatanganan: build mencari keystore proyek di `../keystore/` (tidak ikut ter-commit).
Kalau tidak ada, build otomatis jatuh ke debug signing bawaan Android — APK tetap jadi,
tapi tanda tangannya berbeda dari rilis resmi di repo ini.

## Instal

Download APK dari halaman **Releases**, kirim ke TV (mis. pakai Send Files to TV dari HP),
beri izin "Instal aplikasi yang tidak dikenal" untuk aplikasi pengirimnya, lalu instal.
Jangan set sebagai launcher default sebelum dites manual.

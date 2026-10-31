# BUILD REPORT — Mot TV Launcher, Fase 1 (toolchain + scaffold + APK v1)

Tanggal: 2026-10-27 · Status akhir: **BUILD SUCCESSFUL — APK debug jadi dan terverifikasi badging-nya.**
Belum diuji di TV (TV belum terhubung, sesuai instruksi fase ini memang berhenti di APK).

## Hasil akhir

| Item | Nilai |
|---|---|
| APK (deliverable) | `~/workspace/smart-tv-launcher/MotTvLauncher-0.1.0-debug.apk` |
| APK (output Gradle) | `app-project/app/build/outputs/apk/debug/app-debug.apk` |
| Ukuran | 4.427.334 byte (±4,2 MB) |
| applicationId | `com.mottv.launcher` |
| versionName / versionCode | 0.1.0 / 1 |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 (dari `aapt dump badging` APK final) |
| Leanback activity | `com.mottv.launcher.MainActivity` terdaftar sebagai leanback-launchable + HOME/DEFAULT di manifest |

## Toolchain (semua di workspace, tahan restart VM)

| Komponen | Versi | Lokasi |
|---|---|---|
| JDK Temurin (OpenJDK) | 17.0.21+9 | `~/workspace/tools/jdk/` |
| Android SDK cmdline-tools | 11076708 | `~/workspace/tools/android-sdk/cmdline-tools/latest/` |
| platform-tools (adb) | r37.0.1 (adb 1.0.41) | `~/workspace/tools/android-sdk/platform-tools/` |
| platform android-34 | ext7 r03 | `~/workspace/tools/android-sdk/platforms/android-34/` |
| build-tools | 34.0.0 | `~/workspace/tools/android-sdk/build-tools/34.0.0/` |
| Gradle | 8.9 (binary manual) | `~/workspace/tools/gradle-8.9/` |
| AGP / Kotlin | 8.5.2 / 2.0.20 | plugin Gradle |
| Local Maven repo (offline) | 264 POM + artefak, 188 MB | `~/workspace/tools/maven-local/` |

Lisensi SDK ditulis manual ke `android-sdk/licenses/` (hash lisensi standar sdkmanager) karena sdkmanager tidak bisa jalan — lihat kendala jaringan di bawah.

## Konektivitas & kendala lingkungan (penting, terverifikasi)

- Via **curl**: semua host yang dibutuhkan bisa dijangkau — dl.google.com (200), services.gradle.org (200), maven.google.com (301 → dl.google.com), repo.maven.apache.org (200), plugins.gradle.org (200), api.adoptium.net (200), api.myquran.com (200).
- **Java tidak bisa membuka koneksi TCP sama sekali di VM ini.** Runtime menjawab koneksi TCP dari proses Java dengan teks kebijakan ("Other TCP connections is turned off for this assistant… switch other_tcp from Deny to Ask" di Muse Settings → Permissions → Direct network protocols). Akibat yang terbukti di lapangan:
  - `sdkmanager` gagal total (exception di HTTP CONNECT tunneling) → SDK dipasang **manual via curl** (zip platform-tools/platform/build-tools dari repository XML resmi, layout folder + lisensi ditulis tangan). Hasilnya valid: aapt2/adb jalan.
  - Gradle **daemon** tidak bisa dipakai (IPC loopback ikut ke-intercept, client membaca byte sampah) → build jalan **in-process**: `--no-daemon`, tanpa `org.gradle.jvmargs` di project (heap via `GRADLE_OPTS=-Xmx2560m`), Kotlin compile `in-process`.
  - Gradle tidak bisa download dependensi (HTTP client Java) → semua dependensi **di-prefetch pakai curl** ke local file repo (`~/workspace/tools/maven-local`) dan build pakai `--offline`. `settings.gradle.kts` menaruh file repo ini paling depan; repo remote (google/mavenCentral) tetap dicantumkan sesudahnya untuk mesin normal. Loop iteratif fetch-then-build ada di `~/workspace/smart-tv-launcher/.build/` (`build_loop.sh`, `fetch_dep.py`, log lengkap).
  - `gradle wrapper` task **gagal** (task-nya sendiri butuh akses jaringan Java untuk verifikasi distribusi) → **belum ada gradlew**; build memakai binary Gradle di tools. Wrapper bisa dibuat belakangan di mesin yang jaringan Java-nya normal, atau setelah izin `other_tcp` diubah.
  - Resolusi POM-only (tanpa Gradle module metadata) menghilangkan alignment kotlin-stdlib → `app/build.gradle.kts` meng-exclude `kotlin-stdlib-jdk7/jdk8` lama (1.6.21, ikut dari POM androidx) yang menduplikasi class di stdlib 2.0.20.
- VM **restart sekali di tengah proses** (uptime reset ±02:43 WIB): /tmp hilang, proses build mati. Semua yang penting selamat karena toolchain + repo lokal + project ada di `~/workspace`. Proxy Gradle di-set di `~/.gradle/gradle.properties` (systemProp proxy host hatch-egress-proxy:3128, tanpa kredensial).

## Yang terimplementasi per frame design

| Frame | Status | Catatan kejujuran |
|---|---|---|
| 1 Beranda | ✅ REAL | Tile memakai **banner/icon asli** dari PackageManager (getApplicationBanner → getApplicationIcon) untuk Netflix (candidates `com.netflix.ninja`, `com.netflix.mediaclient`), WeTV (`com.tencent.qqlive`), Vidio (`com.vidio.android.tv`, `com.vidio.android`), Play Store (`com.android.vending`); launch via leanback intent, fallback launcher intent. App yang tidak terinstal → badge "Belum terinstal" + toast saat diklik, tidak crash. Hero (nama + deskripsi + art) mengikuti fokus; fokus = border putih + scale 1.06; jam live per 15 detik; sapaan ikut jam; **quote harian real** dari `assets/quotes.json` (salinan identik md5 dengan `quotes/quotes.json`, index = day-of-year mod 123). Pill Wi-Fi dekoratif (bukan indikator koneksi asli). |
| 2 Games | ✅ REAL | Grid 4 kolom dari app yang ter-query sebagai game (kategori/flag game di ApplicationInfo dari daftar leanback/launcher), art asli, klik = launch. Tile terakhir membuka Play Store. Hitungan "N terinstal" real. Belum ada game → teks kosong jujur. Nama game di VM ini tidak bisa diuji (tidak ada perangkat). |
| 3 Pencarian | ⚠️ SEBAGIAN | BrowserActivity: kolom alamat/pencarian + WebView (JS on), query non-URL diarahkan ke DuckDuckGo HTML. Halaman "hasil pencarian" versi design (kartu + badge kategori) **belum** dibuat — ini browser dasar, bukan halaman hasil custom. |
| 4 Hasil Pencarian | ❌ BELUM | Fase berikutnya (lihat di atas). |
| 5 Pemutar Video | ❌ BELUM | ExoPlayer/Media3 belum dipasang, sesuai rencana fase. Video web saat ini diputar WebView. Konten DRM (Netflix dkk.) tetap di app resmi — sesuai catatan design. |
| 6 Jadwal Bola | 🟨 PLACEHOLDER JUJUR | BolaActivity hanya menampilkan "Modul Jadwal Bola dibangun di fase berikutnya — belum ada datanya, bukan error." **Tidak ada data fixture yang dipalsukan.** |
| 7 Kalender Bola | ❌ BELUM | Bareng modul Bola di fase 2. |
| 8 Jadwal Sholat | ✅ REAL | Data live MyQuran API (Kemenag): daftar 518 kota dari `/v2/sholat/kota/semua` (terverifikasi dari VM: struktur `id`+`lokasi` persis seperti yang di-parse app), dropdown searchable (EditText filter + RecyclerView), default kota tersimpan / "KOTA JAKARTA" (id 1301, dicari by-name, bukan hardcode id), jadwal 8 waktu dari `/v2/sholat/jadwal/{id}/{yyyy}/{MM}/{dd}` (field imsak…isya terverifikasi), highlight + hitung mundur sholat berikutnya (refresh 30 detik), pilihan kota tersimpan di SharedPreferences. Gagal jaringan → pesan error jujur, tanpa data karangan. Catatan: respons API terverifikasi dari VM; perilaku di TV (WebView/network stack Android) belum diuji karena belum ada perangkat. |
| 9 Pengaturan | ✅ SEBAGIAN BESAR REAL | Menu: Background (beneran ganti + tersimpan), Lokasi Sholat (layar penjelasan jujur + tombol ke modul Sholat), Bahasa (placeholder jujur "belum tersedia di 0.1.0"), **Pengaturan TV (Android) ↗ membuka Settings sistem beneran** (intent `android.settings.SETTINGS`), Tentang (info versi + status fase). BackgroundActivity: Abu Gelap/Gunung/Pantai/Kota Malam = **gradasi drawable bawaan, bukan foto** (belum ada foto yang dibundel); Gambar Sendiri via ACTION_OPEN_DOCUMENT dengan persistable permission, disalin ke internal storage ter-downscale maks 1920px (JPEG q85), slider kegelapan overlay 0–80% (default 45%), mode Penuhi Layar/Pas, pratinjau langsung + diterapkan di Beranda (onResume). |

Struktur kode: `app-project/app/src/main/java/com/mottv/launcher/` — MainActivity, GamesActivity, BrowserActivity, SholatActivity, BolaActivity, SettingsActivity, BackgroundActivity, PlaceholderActivity, AppResolver, BackgroundStore, Quotes, Net (HttpURLConnection, tanpa dependensi HTTP tambahan). Dependensi: appcompat 1.7.0, recyclerview 1.3.2, leanback 1.0.0 (terpasang; grid memakai RecyclerView biasa), core-ktx 1.13.1. Manifest: MAIN + HOME + DEFAULT + LEANBACK_LAUNCHER, uses-feature leanback/touchscreen `required=false`, izin INTERNET + QUERY_ALL_PACKAGES (perlu untuk visibilitas paket launcher), banner TV ada.

## Keterbatasan yang diketahui

1. **Belum pernah jalan di perangkat/emulator** — tidak ada TV terhubung dan emulator belum dipasang; semua verifikasi runtime (fokus D-pad, banner app asli, picker dokumen di TV, WebView) masih menunggu uji perangkat. Yang terverifikasi: kompilasi, badging APK, struktur API MyQuran, isi quotes (123 quote terbaca dari assets).
2. Gradle wrapper belum ada (lihat kendala jaringan); perintah build saat ini:
   `JAVA_HOME=~/workspace/tools/jdk GRADLE_USER_HOME=~/.gradle GRADLE_OPTS="-Xmx2560m -Dfile.encoding=UTF-8" ~/workspace/tools/gradle-8.9/bin/gradle assembleDebug --offline --no-daemon` dari `app-project/`.
3. Background bawaan Gunung/Pantai/Kota Malam masih gradasi warna (placeholder visual), bukan foto.
4. Pill Wi-Fi di Beranda dekoratif.
5. minSdk 26 asumsi — model/versi Android TV user belum dicek; kalau TV ternyata lebih tua dari Android 8, minSdk perlu diturunkan dan adaptive icon dievaluasi.
6. APK debug (ditandatangani debug key), belum ada release signing.

## Langkah berikutnya (fase 2, usulan)

1. Uji di TV: ADB Wi-Fi → install APK, cek banner asli Netflix/WeTV/Vidio, fokus, picker gambar, dan data Sholat live di perangkat; baru setelah itu urusan set-default launcher (launcherx + setupwraith + skrip restore) dan debloat per persetujuan user.
2. Modul Jadwal Bola real (API-Football/TheSportsDB + kalender bebas + logo liga) dan halaman Hasil Pencarian + Pemutar (Media3) sesuai design.
3. Buat Gradle wrapper + pertimbangkan release build bertanda tangan.
4. Kalau user mengizinkan `other_tcp` = Ask di pengaturan Muse, toolchain bisa dinormalisasi (sdkmanager + daemon + wrapper + repo online).

---

# BUILD REPORT — Fase 2 (Pencarian/Hasil/Player + Jadwal Bola)

Tanggal: 2026-10-27 · Status akhir: **BUILD SUCCESSFUL — APK 0.2.0 jadi dan terverifikasi badging-nya.**
Sama seperti fase 1: **belum diuji di perangkat** (TV masih belum terhubung; tidak ada percobaan ADB). Semua status di bawah adalah *compile-verified + API-verified dari VM*, bukan runtime-verified di TV.

## Hasil akhir

| Item | Nilai |
|---|---|
| APK (deliverable) | `~/workspace/smart-tv-launcher/MotTvLauncher-0.2.0-debug.apk` |
| Ukuran | 8.411.403 byte (±8,4 MB — naik dari 4,2 MB karena Media3) |
| applicationId | `com.mottv.launcher` |
| versionName / versionCode | **0.2.0 / 2** (dari `aapt dump badging`) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 (badging) |
| Activity baru di manifest APK | SearchActivity, ResultsActivity, PlayerActivity, CalendarActivity, BolaActivity (total 12 activity) |
| Sanity | `assets/quotes.json` md5 identik dengan `quotes/quotes.json` (1600905b…) — pool quote tidak berubah; SholatActivity tidak disentuh di fase ini |

Perintah build yang hijau (persis pola fase 1): `JAVA_HOME=~/workspace/tools/jdk GRADLE_USER_HOME=~/.gradle GRADLE_OPTS="-Xmx2560m -Dfile.encoding=UTF-8" ~/workspace/tools/gradle-8.9/bin/gradle assembleDebug --offline --no-daemon` dari `app-project/`. Loop `.build/build_loop.sh` dipakai buat iterasi fetch dependensi (guava dll. ketemu otomatis).

**Catatan versi Media3:** rencana awal 1.5.1 **gagal** di `checkDebugAarMetadata` — metadata AAR 1.5.1 menuntut compileSdk ≥35 (11 issues), sementara toolchain proyek compileSdk 34/AGP 8.5.2. Turun ke **Media3 1.4.1** (baris terakhir yang menerima compileSdk 34); semua artefak (exoplayer, exoplayer-hls, exoplayer-dash, ui, session + common/container/extractor/decoder/database/datasource) ter-prefetch via curl ke maven-local seperti biasa. API yang dipakai (ExoPlayer, PlayerView, trackSelectionParameters) sama di 1.4.1.

## Yang terimplementasi per frame

| Frame | Status | Catatan kejujuran |
|---|---|---|
| 3 Pencarian | ✅ REAL (compile-verified) | `SearchActivity`: input besar, chip kategori (Semua/Film/Musik/Video/Web), **keyboard on-screen grid 10 kolom full D-pad** (huruf+angka+Spasi/Hapus/Cari), recent searches persisten di SharedPreferences (JSON, maks 8, terbaru di depan, klik = cari lagi). Tile Browser di Beranda sekarang membuka halaman ini. Input yang terlihat seperti URL → langsung dibuka di BrowserActivity (WebView); selain itu → ResultsActivity. Pill mic hanya label petunjuk remote (voice input tergantung remote/OS, bukan fitur app). |
| 4 Hasil Pencarian | ✅ REAL (compile-verified; endpoint terverifikasi dari VM) | `ResultsActivity`: fetch **DuckDuckGo HTML** (`html.duckduckgo.com/html/?q=…&kl=id-id`) via HttpURLConnection di background thread. **Syarat terverifikasi:** endpoint ini menyajikan halaman *anomaly/robot-check* ke user-agent tak dikenal; dengan UA browser realistis ia mengembalikan 10 hasil beneran (diverifikasi dari VM: 10 link `result__a` + 10 snippet). Parser mengikuti struktur terverifikasi itu: judul dari `result__a`, URL asli di-decode dari parameter `uddg`, snippet dari `result__snippet`. Chip kategori me-query ulang dengan petunjuk kata (Film → "+film", Musik → "+musik lagu", Video → "+video"). Thumbnail = **og:image halaman hasil**, di-fetch malas (maks 6 hasil pertama, baca ≤48 KB head) dan tampil saat tiba; sebelum/selain itu placeholder gradien+inisial domain (fallback design, bukan thumbnail karangan). Badge hanya yang bisa ditentukan dari URL: `MEDIA` (file media langsung) / `VIDEO` (host video dikenal). Klik hasil: file media langsung → PlayerActivity; selain itu → BrowserActivity. State jujur: halaman robot-check DDG dideteksi & ditampilkan sebagai pesan batasan, hasil 0 = "Tidak ada hasil", gagal jaringan = pesan error. |
| 5 Pemutar Video | ✅ REAL (compile-verified) | `PlayerActivity` + Media3 ExoPlayer 1.4.1 (+HLS/DASH): fullscreen, kontrol D-pad custom (OK = play/pause, kiri/kanan = ∓10 dtk saat kontrol tersembunyi, navigasi fokus tombol saat kontrol tampil, tombol ⏮/−10/+10/⏭, subtitel on/off kalau track teks ada — kalau tidak ada, toast jujur), seek bar + waktu berjalan, judul + domain sumber di atas, **auto-hide kontrol 4 detik** (hanya saat playing), Back = keluar. URL non-media **tidak** diputar paksa: dideteksi di awal dan dialihkan ke BrowserActivity. Error pemutaran tampil apa adanya + tombol "Buka di Browser". DRM (Netflix dkk.) tidak pernah dirutekan ke sini. |
| 6 Jadwal Bola | ✅ REAL (compile-verified; API terverifikasi dari VM) | `BolaActivity` ditulis ulang total (placeholder fase 1 dibuang). Data **TheSportsDB free API (key "123")**, query **per liga**: `eventsday.php?d=<tanggal>&s=Soccer&l=<idLiga>`. Pill tanggal Kemarin/Hari Ini/Besok + label tanggal terpilih; grup per liga: logo liga di header grup + tag LIGA/CUP; baris match: badge+nama home — jam kickoff **WIB** (konversi dari timestamp UTC) atau skor untuk match selesai/live + status "Ditunda" — nama+away badge; pill platform streaming paling kanan. Cache in-memory per tanggal hanya untuk data yang berhasil di-fetch utuh. |
| 7 Kalender Bola | ✅ REAL (compile-verified) | `CalendarActivity`: grid bulan (awal Senin), navigasi D-pad antar hari, tombol ganti bulan **dan tahun**, tanggal hari ini ditandai emas, **tanggal berapa pun bisa dipilih** (termasuk 11 Feb 2027 dari contoh design). Tanggal terpilih balik ke BolaActivity yang langsung memuat jadwal tanggal itu. |
| 8 Jadwal Sholat | ↔ TIDAK DIUBAH | Sesuai instruksi fase ini; perilakunya tetap yang terverifikasi di fase 1. |

## Bukti verifikasi TheSportsDB (dari VM, 2026-10-27, sebelum coding)

- `eventsday.php` (tanpa filter liga) di key gratis hanya mengembalikan **sampel kecil** (3 event) — terbukti tidak lengkap: untuk 2026-10-24 ia hanya menampilkan USL, padahal query per-liga di tanggal sama menemukan match PL/La Liga/Championship/Bundesliga. Karena itu app **selalu query per liga**. Konsekuensi jujur: per-liga pun tidak pernah mengembalikan >3 event per hari dalam semua pengujian (8 kombinasi liga×tanggal) — kemungkinan besar batas tier gratis; app menampilkan persis yang dikembalikan API dan tidak mengarang fixture tambahan.
- Field event terverifikasi ada: `strHomeTeamBadge`, `strAwayTeamBadge`, `strLeagueBadge` (URL PNG `r2.thesportsdb.com`), `strTimestamp` (UTC, mis. `2026-10-24T14:00:00` → 21:00 WIB), `strStatus` (`NS`/`FT`/…), `intHomeScore`/`intAwayScore`, `strPostponed`. Jadi badge tim **tidak** perlu lookup terpisah; badge liga buat chip diambil dari `lookupleague.php` (sekali per sesi, fallback inisial).
- **League ID terverifikasi dari API-nya sendiri** (bukan tebakan): `all_leagues.php` → 4328 English Premier League, 4331 German Bundesliga, 4332 Italian Serie A, 4335 Spanish La Liga; `search_all_leagues.php?c=Indonesia&s=Soccer` → 4790 Indonesian Super League (nama resmi liga di sumbernya; ditampilkan di UI sebagai "Liga 1 Indonesia"); `lookupleague.php?id=4480` → UEFA Champions League + badge. Keenamnya punya `strBadge` terkonfirmasi via lookupleague.

## Tabel platform streaming & kategori (MANUAL, per musim)

TheSportsDB **tidak** menyediakan data penyiar Indonesia. Tabel ini dipelihara manual di kode (`BolaActivity.LEAGUES`) untuk musim **2025/26** dan harus ditinjau ulang tiap musim:

| Liga (ID) | Kategori | Platform di UI |
|---|---|---|
| English Premier League (4328) | LIGA | Vidio · SCTV |
| Spanish La Liga (4335) | LIGA | beIN Sports |
| Italian Serie A (4332) | LIGA | beIN Sports |
| German Bundesliga (4331) | LIGA | — → tampil "Platform: cek penyedia" (tidak cukup yakin, tidak ditebak) |
| UEFA Champions League (4480) | CUP | SCTV · beIN Sports |
| Indonesian Super League (4790) | LIGA | Indosiar · Vidio |

## Keterbatasan fase 2 (jujur)

1. **Belum pernah jalan di perangkat/emulator** — semua modul baru compile-verified + (untuk Bola & Hasil) endpoint-nya terverifikasi dari VM via curl dengan pola request yang sama. Perilaku runtime di TV (fokus D-pad antar tombol player, WebView, rendering badge, og:image dari jaringan rumah user) masih menunggu uji perangkat, sama seperti catatan fase 1.
2. Tier gratis TheSportsDB membatasi jumlah event (lihat bukti di atas) — jadwal yang tampil bisa lebih sedikit dari jadwal sebenarnya di hari padat. Upgrade key premium adalah keputusan user, bukan yang dikerjakan di sini.
3. DuckDuckGo bisa menampilkan robot-check tergantung jaringan/IP; app menampilkannya sebagai pesan batasan, bukan hasil kosong palsu.
4. Thumbnail hasil pencarian bergantung og:image situs sumber; situs tanpa og:image tetap placeholder gradien+inisial.
5. Platform streaming & kategori LIGA/CUP adalah tabel manual 2025/26 (di atas), bukan data API.
6. Jam kickoff TheSportsDB kadang berubah (jadwal TV); app menampilkan data API apa adanya per tanggal yang dipilih, tanpa cache lintas sesi.


---

# BUILD REPORT — Fase 3 (perbaikan dari uji TV pertama, v0.3.0)

Tanggal: 2026-10-31 · Status akhir: **BUILD SUCCESSFUL — APK 0.3.0 jadi dan terverifikasi badging-nya.**
Konteks baru: app **sudah terinstal dan diuji user di TV asli** (TCL 32S51K, 32", 1366×768, Android 14 — minSdk 26 aman). Fase ini adalah iterasi perbaikan dari 7 catatan user atas 0.2.0. Status di bawah tetap dibedakan jujur: semua *compile-verified*; perilaku runtime perbaikan **belum terkonfirmasi di TV** sampai user menginstal 0.3.0 dan mencoba lagi (tidak ada percobaan ADB dari VM).

## Hasil akhir

| Item | Nilai |
|---|---|
| APK (deliverable) | `~/workspace/smart-tv-launcher/MotTvLauncher-0.3.0-debug.apk` |
| Ukuran | 9.219.230 byte (±9,2 MB — naik ±0,8 MB dari 3 foto background terbundel) |
| applicationId | `com.mottv.launcher` |
| versionName / versionCode | **0.3.0 / 3** (dari `aapt dump badging`) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 (badging) |
| Izin baru di badging | `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` (indikator status Wi-Fi asli) |
| Sanity | `assets/quotes.json` md5 identik dengan `quotes/quotes.json` (1600905b…) — pool tidak berubah; endpoint Bola & Sholat tetap yang terverifikasi fase 1–2 |

Perintah build hijau dalam sekali jalan (tanpa loop fetch — tidak ada dependensi baru): pola offline fase 1 persis (`assembleDebug --offline --no-daemon`, JDK tools, GRADLE_OPTS heap). Satu-satunya warning: `FLAG_IS_GAME` deprecated di GamesActivity (sudah ada sejak fase 1, tidak berpengaruh).

## Perbaikan per catatan user

| # | Catatan user | Yang diubah | Status |
|---|---|---|---|
| 1a | Jadwal Bola "gagal memuat" di TV | **Diagnosis berlapis, semua ditangani sekaligus:** (i) UA custom app `MotTvLauncher/0.2.0` diganti **browser UA realistis untuk SEMUA request** di `Net` (pola yang terbukti membuka DuckDuckGo di fase 2 — TheSportsDB tampaknya menolak UA tak dikenal dari jaringan TV); (ii) timeout naik 10s/15s → **15s/20s**; (iii) muat liga sekarang **sekuensial** (dulu burst paralel dari pool 3 thread) dengan jeda 350 ms antar-liga; (iv) **1× retry backoff 1,6 dtk** untuk HTTP 429/5xx & error I/O; (v) error sekarang **diagnostik**: `Net` melempar `HttpException(code)`, state gagal menampilkan kode aslinya (mis. "Gagal memuat jadwal (HTTP 429) — … sebutkan kode ini kalau lapor"); (vi) **cache persisten** (SharedPreferences `bola_cache`): hari yang pernah termuat utuh disimpan; kalau pemuatan ulang gagal total, data tersimpan ditampilkan **dengan label terbuka** "Menampilkan data tersimpan dari HH:mm — pemuatan ulang gagal (…)"; gagal sebagian → liga yang berhasil tetap tampil + status menyebut jumlah & error terakhir. Pola request persisnya diverifikasi ulang dari VM saat fase ini: eventsday Liga 1 hari ini + browser UA → **HTTP 200, event asli** (Arema vs Bali United). | ⚠️ Compile-verified + pola request terverifikasi dari VM. **Belum terkonfirmasi sembuh di TV** — itu hanya bisa dibuktikan user dengan menginstal 0.3.0. Kalau masih gagal, pesan error baru akan menunjukkan kode HTTP-nya sehingga diagnosis berikutnya berbasis bukti, bukan tebakan. |
| 1b | Liga Indonesia diprioritaskan | Urutan `LEAGUES` diubah: **4790 Liga 1 Indonesia PERTAMA**, lalu Premier League (4328), Champions League (4480), La Liga (4335), Serie A (4332), Bundesliga (4331). Urutan ini sekaligus mengatur urutan grup di halaman, urutan chip setelah "Semua", dan urutan pemuatan sekuensial. | ✅ Compile-verified |
| 2 | Sholat: list kota jangan auto-muncul; default Bandung; info kepotong | Layout ditulis ulang total: halaman membuka **jadwal langsung** (kota tersimpan; **default pertama kali = KOTA BANDUNG**, menggantikan Jakarta — dicari by-name dari daftar API, bukan hardcode id). List kota **hilang dari layout awal**; muncul hanya sebagai panel picker setelah tombol **"Ganti Kota"** (pill emas, fokus jelas) ditekan; memilih kota menyimpan + menutup picker; **Back menutup picker tanpa mengubah kota**. Anti-kepotong: konten jadwal dibungkus **ScrollView**; 8 waktu sholat sekarang **grid tile 4×2 kompak** (tile 76dp, label kecil + jam 21sp) menggantikan daftar vertikal 8 baris tinggi yang overflow di kolom kanan 768p; kartu "Sholat berikutnya" menonjol (nama 31sp emas + countdown pill emas); tile yang sudah lewat diredupkan, tile berikutnya emas penuh. | ✅ Compile-verified; perbaikan clipping berbasis audit layout lama (kolom kanan fixed + 8 baris × ±41dp memang melebihi tinggi panel 720p) — konfirmasi visual tetap menunggu uji TV user. |
| 3 | Indikator "Wi-Fi" jangan kayak tombol | Pill Wi-Fi **dibuang**. Diganti indikator status pasif: `WifiIconView` custom (busur sinyal digambar canvas + titik, slash merah samar saat offline) + teks polos **tanpa background/border pill, tidak focusable, tidak clickable**. Isinya **data asli** dari ConnectivityManager/WifiManager: SSID yang terhubung bila terbaca (di Android modern SSID butuh izin lokasi — tanpa itu tampil jujur "Wi-Fi Terhubung") + level sinyal ("Kuat/Sedang/Lemah/Sangat lemah" dari `calculateSignalLevel`), atau "Tidak terhubung". Refresh saat resume + tiap 15 detik (tick jam). Izin ACCESS_NETWORK_STATE/ACCESS_WIFI_STATE ditambahkan. | ✅ Compile-verified |
| 4 | Background Gunung dll. kok ga ada gambarnya | Benar — di 0.1–0.2 ketiganya cuma gradasi drawable. Sekarang **foto generate asli dibundel**: 3 JPG di `res/drawable-nodpi/` (`bg_photo_gunung/pantai/kota.jpg`, 1920×1080, q80, total ±0,79 MB dari file sumber 2048px di `~/workspace/smart-tv-launcher/backgrounds/`), `BackgroundStore.drawableResFor` menunjuk ke foto; overlay kegelapan + pratinjau BackgroundActivity bekerja seperti sebelumnya (pratinjau = background halaman itu sendiri). "Abu Gelap" tetap gradasi default; "Gambar Sendiri" tidak berubah. Isi APK diverifikasi memuat ketiga foto. | ✅ Compile-verified + isi APK terverifikasi |
| 5 | Thumbnail aplikasi di Beranda "burik" | Akar masalah: 0.2.0 men-stretch drawable apa pun (sering ikon resolusi rendah) memenuhi tile 16:9 via CENTER_CROP. Sekarang `AppResolver` mengembalikan **Artwork terpisah** (banner + ikon + lebar banner px) dan aturan render baru (`TileArt`): banner hanya memenuhi tile bila **lebar ≥320px**; selain itu **ikon ditampilkan di tengah pada ukuran wajar** (kotak 58dp di Beranda, padding 26dp di Games) di atas gradasi brand tile — tidak pernah di-stretch; `BitmapDrawable.setFilterBitmap(true)` diaktifkan agar scaling halus. Hero mengikuti aturan yang sama (banner tajam → crop penuh; selain itu ikon centered di atas frame gradasi). Games grid memakai perlakuan identik. | ✅ Compile-verified |
| 6 | Tambah YouTube di Beranda | Tile **YouTube** ditambahkan: urutan row jadi Browser, **YouTube**, Netflix, WeTV, Vidio, Games, Play Store (**7 tile** — margin antar tile 6→5dp, tinggi row 126→118dp). Kandidat paket `com.google.android.youtube.tv` lalu `com.google.android.youtube`; perilaku banner asli + badge "Belum terinstal" sama seperti tile lain; `<queries>` manifest ditambah kedua paket. | ✅ Compile-verified |
| 7 | Improve & percantik tampilan | Hero kini punya **glow radial lembut berwarna brand tile yang sedang fokus** (design v2 intent; digambar sebagai satu GradientDrawable statis per perubahan fokus — tanpa blur runtime berat). Tile Beranda & baris match Bola dapat elevation halus; radius sudut diseragamkan (tile 14dp, tile sholat 12dp, kartu 12dp); tipografi disetel ulang untuk 768p (nama kota 25sp, sholat berikutnya 31sp, jam grid 21sp). Fokus tetap border putih + scale di semua halaman. | ✅ Compile-verified |

## Keterbatasan fase 3 (jujur)

1. **Perbaikan Jadwal Bola belum terbukti di TV.** Yang terverifikasi: kompilasi, badging, dan pola request yang sama persis dari VM (HTTP 200 + data asli). Jaringan rumah user (DNS/ISP/IPv6 di TV) tidak bisa disimulasikan dari VM; kalau masih gagal, error state 0.3.0 menampilkan kode HTTP/exception-nya — laporkan kode itu untuk diagnosis lanjutan.
2. Cache bola hanya menyimpan hari yang **pernah termuat utuh**; tanggal yang belum pernah berhasil tidak punya data tersimpan (by design — tidak ada data karangan).
3. SSID Wi-Fi kemungkinan besar tampil sebagai "Wi-Fi Terhubung" generik di Android 14 tanpa izin lokasi; app sengaja tidak meminta izin lokasi hanya untuk label ini. Level sinyal tetap asli.
4. Kualitas banner tetap bergantung aset yang disediakan tiap app di TV; aturan ≥320px mencegah yang burik di-stretch, tapi app tanpa banner & tanpa ikon bagus akan tampil sebagai ikon sederhana di atas gradasi — itu batas data sumber, ditampilkan apa adanya.
5. Foto background adalah gambar generate (bukan foto lokasi nyata) — sesuai aset yang disiapkan untuk proyek ini.


## Addendum Fase 3 — konflik tanda tangan & kunci permanen (2026-11-01)

- Gejala di TV user: instal 0.3.0 di atas 0.2.0 ditolak "paket bentrok". Akar masalah terverifikasi dengan apksigner: sertifikat 0.1.0 & 0.2.0 identik (SHA-256 69e007b1…), sedangkan 0.3.0 beda (98085b06…) — kunci debug otomatis berubah antar sesi build (keystore debug ephemeral, `~/.android` kosong, kunci lama tidak ditemukan di seluruh filesystem → tidak bisa di-resign dengan kunci lama).
- Perbaikan permanen: keystore proyek khusus dibuat di `~/workspace/smart-tv-launcher/keystore/mot-launcher.keystore` (alias `motlauncher`, SHA-256 DD:A6:FA:98:27:73:7D:4B:44:7A:3C:AA:47:0F:BE:C0:83:8D:53:46:79:F4:57:9A:01:95:AE:11:6F:E0:96:D4; password di `keystore.properties` chmod 600 di folder yang sama) dan di-wire sebagai signingConfig "mot" untuk debug+release di `app/build.gradle.kts`.
- APK 0.3.0 di-build ulang: BUILD SUCCESSFUL, sertifikat APK final = dda6fa98… (cocok keystore), badging tetap 0.3.0/vc3/minSdk 26. File deliverable `MotTvLauncher-0.3.0-debug.apk` sudah diganti dengan build bertanda tangan permanen ini.
- Konsekuensi untuk user: SATU KALI uninstall manual (0.2.0 berkunci lama) sebelum instal 0.3.0 ini; setelah itu semua update berikutnya (kunci permanen sama) bisa ditimpa langsung tanpa konflik. Data yang hilang saat uninstall: kota tersimpan (default baru memang Bandung), gambar background custom (perlu pilih ulang), riwayat pencarian.


---

# BUILD REPORT — Fase 4 (retest TV 0.3.0: Bola hybrid ESPN, thumbnail no-crop, Semua App) — v0.4.0

Tanggal: 2026-11-01 · Status akhir: **BUILD SUCCESSFUL — APK 0.4.0 jadi, badging + sertifikat terverifikasi.**
Konteks: retest user atas 0.3.0 di TV asli menghasilkan 3 catatan: (1) Jadwal Bola **masih HTTP 429**, (2) thumbnail Beranda **kepotong**, (3) minta **satu tombol buat liat semua installed apps**. Fase ini mengerjakan ketiganya. Status di bawah tetap dibedakan jujur: semua *compile-verified + API terverifikasi dari VM*; konfirmasi runtime di TV menunggu instal 0.4.0 oleh user (tidak ada percobaan ADB dari VM).

## Hasil akhir

| Item | Nilai |
|---|---|
| APK (deliverable) | `~/workspace/smart-tv-launcher/MotTvLauncher-0.4.0-debug.apk` |
| Ukuran | 9.232.817 byte (±9,2 MB — praktis sama dengan 0.3.0; tidak ada dependensi baru) |
| applicationId | `com.mottv.launcher` |
| versionName / versionCode | **0.4.0 / 4** (dari `aapt dump badging`) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 (badging) |
| Sertifikat penanda tangan | SHA-256 **dda6fa9827737d4b447a3caa470fbec0838d534679f4579a0195ae116fe096d4** — identik dengan keystore permanen proyek (sama dengan 0.3.0 hasil rebuild berkunci tetap) → **0.4.0 bisa diinstal menimpa 0.3.0 TANPA uninstall** |
| Activity baru | `AllAppsActivity` (terverifikasi ada di manifest APK via aapt xmltree) |
| Sanity | `assets/quotes.json` md5 identik dengan `quotes/quotes.json` (1600905b…) — pool tidak berubah; signingConfig "mot" tidak diubah |

Perintah build hijau: pola offline persis fase 1–3 (`assembleDebug --offline --no-daemon`). Satu insiden build yang sudah diatasi: percobaan menampilkan versi via `BuildConfig` (buildConfig=true) membuat `compileDebugJavaWithJavac` butuh Gradle Worker Daemon yang tidak bisa jalan di VM ini (IPC loopback ke-intercept, persis kendala fase 1) → dibatalkan; versi di Pengaturan sekarang dibaca runtime dari PackageManager (sekalian membetulkan string basi "v0.1.0" yang tertinggal sejak fase 1 di menu Tentang/Bahasa).

## Item 1 — Jadwal Bola: strategi sumber diganti jadi HYBRID

**Akar masalah (identifikasi parent, bukti dari VM):** tier gratis TheSportsDB memakai kunci demo publik `"123"` yang dipakai bersama sedunia → rate limit-nya struktural; UA/retry/cache fase 3 tidak bisa menyembuhkannya (di TV tetap 429 untuk semua liga).

**Strategi baru — rantai sumber per liga** (`BolaActivity.fetchLeagueDay`; fallback hanya saat sumber utama *error*, tidak pernah saat hari memang kosong):

| Liga | Utama | Cadangan | Alasan |
|---|---|---|---|
| Liga 1 Indonesia (4790 / idn.1) | **TheSportsDB** | ESPN idn.1 | ESPN idn.1 **kosong untuk musim 2026/27** (terverifikasi 0 event di 2026-10-25/31, 2026-11-01/07, padahal punya data 2025); TSDB 4790 **punya** match-nya (terverifikasi 2026-10-31: Arema–Bali Utd, PSM–Madura; 2026-11-01: PSIM–Borneo, Persib–Isenmulang Kalteng, Garudayaksa–PSS, 3 event) |
| Premier League (eng.1), La Liga (esp.1), Serie A (ita.1), Bundesliga (ger.1), Champions League (uefa.champions) | **ESPN** (tanpa kunci) | TheSportsDB | ESPN mengembalikan **jadwal penuh** tanpa batas 3-event dan tanpa kunci bersama |

**Implementasi ESPN** (`fetchEspn`/`parseEspn`/`espnLeagueLogo`/`parseEspnKickoff`): endpoint `site.api.espn.com/apis/site/v2/sports/soccer/{slug}/scoreboard?dates=YYYYMMDD` dengan browser UA yang sama; tanggal event ISO-8601 UTC dikonversi ke WIB; competitors diurutkan via `homeAway`; skor string hanya dipakai saat state bukan `pre`; state `in` → status LIVE + `displayClock` ditampilkan sebagai "Sedang main · 67'"; state `post` → FT/skor; `STATUS_POSTPONED` → Ditunda. Logo klub = `team.logo` (PNG 500px dari a.espncdn.com); logo liga = `leagues[0].logos` varian **default** (bukan dark) di lingkaran putih sesuai design. Logo liga chip sekarang juga diisi dari ESPN dulu (satu panggilan scoreboard per liga — nol tekanan ke kunci bersama), lookupleague TSDB tinggal cadangan.

**Yang dipertahankan persis dari fase 2–3:** urutan muat sekuensial Liga Indonesia pertama + jeda 350 ms, `fetchWithRetry` (1× retry backoff 1,6 dtk untuk 429/5xx/IO), timeout 15s/20s, cache persisten data asli dengan label terbuka "data tersimpan dari HH:mm", state error menampilkan kode HTTP asli, tabel platform manual 2025/26 + tag LIGA/CUP, kalender tanggal bebas. Cache lama tetap terbaca (field baru `liveClock` opsional).

**Bukti verifikasi dari VM (2026-11-01, sebelum & sesudah coding):** ESPN eng.1 hari ini HTTP 200 — event Liverpool–Arsenal lengkap dengan URL logo tim 500px dan dua varian logo liga; eng.1 kemarin (2026-10-31) **8 event** (jadwal penuh, bukan sampel 3); idn.1 HTTP 200 dengan 0 event (konsisten dengan temuan parent); TSDB 4790 hari ini HTTP 200, 3 event asli dengan badge.

**Kejujuran status fix 429:** yang terverifikasi adalah kompilasi + pola request dari VM. **Bukti sembuh di TV tetap menunggu retest user.** Yang bisa dinyatakan pasti: 5 dari 6 liga tidak lagi menyentuh kunci demo bersama sama sekali, jadi jalur 429 lama tertutup untuk mereka; Liga Indonesia masih bergantung pada kunci bersama itu (tidak ada sumber gratis lain yang punya datanya musim ini), tetapi sekarang ia dimuat **pertama dan sendirian** di awal urutan — peluang terbaik yang tersedia secara gratis. Catatan kecil: pengelompokan tanggal ESPN mengikuti hari versi ESPN (berbasis AS); jam tayang yang tampil tetap hasil konversi WIB dari timestamp UTC event-nya.

## Item 2 — Thumbnail tidak lagi kepotong (no-crop)

Akar masalah: fase 3 memakai banner tajam (≥320px) untuk **mengisi penuh** tile dengan CENTER_CROP → tepi artwork terpotong di TV. Sekarang:

- Komponen baru `FitArtView` (dua lapis): lapisan belakang = salinan bitmap yang sama, di-blur (downscale ÷16 → upscale berfilter, dirender sekali & di-cache per bitmap di `TileArt.blurredBackdrop`) + digelapkan (overlay hitam α=105), di-CENTER_CROP (blur boleh terpotong, tidak ada informasi hilang); lapisan depan = artwork **asli FIT_CENTER** — seluruh banner selalu tampil utuh, letterbox diisi blur-nya sendiri.
- Berlaku di **tile Beranda, preview hero, grid Games, dan grid Semua App**. Perlakuan ikon (centered di atas gradasi brand, aturan ≥320px) tidak berubah — ikon memang tidak pernah di-crop.
- Tidak ada dependensi/library gambar baru; semua pakai Bitmap/Canvas bawaan.

Status: compile-verified; perbaikan berbasis akar masalah yang jelas (scaleType), konfirmasi visual tetap di retest TV user.

## Item 3 — Tombol "Semua App" + halaman All Apps

- **Tombol** `▦ Semua App` ditambahkan di top bar Beranda, di sisi kanan sebelum Pengaturan, dengan styling pill fokusable yang sama seperti tombol lain — disengaja berbentuk tombol karena memang tombol (kontras dengan indikator Wi-Fi pasif di sebelahnya).
- **`AllAppsActivity`**: grid **6 kolom**; query `ACTION_MAIN` + kategori LEANBACK_LAUNCHER lalu LAUNCHER, dedupe per paket (entri leanback menang), paket sendiri (`com.mottv.launcher`) dikeluarkan, urut label A–Z dari `loadLabel` asli; artwork aturan yang sama dengan Beranda (banner utuh no-crop / ikon centered); **label di bawah artwork** (bukan overlay seperti Games); klik membuka app (leanback intent → launcher intent → intent komponen eksplisit dari ResolveInfo); header pill menampilkan hitungan asli "**N aplikasi**"; query gagal/kosong → state kosong jujur "Tidak ada aplikasi lain yang bisa dibuka di TV ini."
- Manifest: activity terdaftar (exported=false); tidak ada izin baru (QUERY_ALL_PACKAGES sudah ada sejak fase 1).

## Bonus kecil (di luar 3 item, dibetulkan sekalian karena menyangkut klaim versi)

Menu **Tentang** dan **Bahasa** di Pengaturan masih menampilkan string basi "v0.1.0" dari fase 1 (dan Tentang masih menyebut Bola/Player "menyusul"). Sekarang versi dibaca runtime dari PackageManager (selalu benar di versi berapa pun) dan isi Tentang mencerminkan modul yang benar-benar ada di 0.4.0.

## Keterbatasan fase 4 (jujur)

1. **Fix 429 belum terbukti di TV** — lihat item 1. Yang tertutup pasti adalah jalur kunci-bersama untuk 5 liga besar; Liga Indonesia tetap di kunci bersama (dimuat pertama, sendirian) karena ESPN kosong untuk musim ini dan tidak ada alternatif gratis terverifikasi lain.
2. Semua perubahan fase ini compile-verified + API-verified dari VM; belum ada verifikasi runtime/emulator (tidak ada perangkat/ADB dari VM, sama seperti fase sebelumnya).
3. Backdrop blur adalah pendekatan downscale/upscale (bukan gaussian blur sejati) — cukup untuk latar letterbox dan nyaris tanpa biaya runtime; kualitasnya dinilai di TV.
4. Grid Semua App menampilkan semua app yang punya intent launchable — termasuk app bawaan TV yang memang bisa dibuka dari app drawer stok; app sistem tanpa launcher entry tidak muncul (sesuai definisi "user-launchable").


---

# BUILD REPORT — Fase 4.1 (patch UI kecil dari retest 0.4.0) — v0.4.1

Tanggal: 2026-11-01 · Status akhir: **BUILD SUCCESSFUL — APK 0.4.1 jadi, badging + sertifikat terverifikasi.**
Konteks: dua catatan user atas 0.4.0 yang sudah terinstal di TV: (1) tombol Semua App di top bar bikin bar atas "kepanjangan", minta dipindah ke samping quote; (2) indikator Wi-Fi cukup ikon aja. Hanya dua perubahan ini — tidak ada perubahan perilaku lain, tidak ada dependensi baru, signing tidak disentuh.

## Hasil akhir

| Item | Nilai |
|---|---|
| APK (deliverable) | `~/workspace/smart-tv-launcher/MotTvLauncher-0.4.1-debug.apk` |
| Ukuran | 9.232.657 byte (±9,2 MB — praktis sama dengan 0.4.0) |
| applicationId | `com.mottv.launcher` |
| versionName / versionCode | **0.4.1 / 5** (dari `aapt dump badging`) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 (badging) |
| Sertifikat penanda tangan | SHA-256 **dda6fa9827737d4b447a3caa470fbec0838d534679f4579a0195ae116fe096d4** — identik keystore permanen → **0.4.1 bisa diinstal menimpa 0.4.0 TANPA uninstall** |
| Sanity | `assets/quotes.json` md5 identik dengan `quotes/quotes.json` (1600905b…) — pool tidak berubah; tidak ada sisa referensi `wifiText` di source (grep bersih) |

Perintah build hijau sekali jalan: pola offline persis fase 1–4 (`assembleDebug --offline --no-daemon`, 15 task dieksekusi).

## Perubahan

1. **Tombol "▦ Semua App" pindah ke samping quote.** Dihapus dari top bar (bar atas sekarang: pill Beranda/Bola/Sholat — spacer — ikon Wi-Fi — Pengaturan — jam). Di layout Beranda, quote sekarang satu baris horizontal: `quoteLine` (lebar 0dp + weight 1, `maxLines=2`, `ellipsize=end`) di kiri dan tombol Semua App (id `pillAllApps` yang sama, styling pill fokusable yang sama) di ujung kanan dengan lebar mengikuti kontennya — quote yang panjang memampat di ruangnya sendiri dan **tidak bisa mendorong tombol keluar layar**. Handler klik/fokus di `MainActivity` tidak berubah (id sama, tetap membuka `AllAppsActivity`). Graf fokus tetap geometris bawaan Android: dari tile, tombol Atas mendarat natural ke tombol di baris quote; dari pill atas, Bawah tetap hero → baris quote/tombol → tile seperti sebelumnya.
2. **Indikator Wi-Fi jadi ikon saja.** TextView `wifiText` (SSID + kata level) dihapus dari layout dan dari `MainActivity` (field, lookup, dan assignment dibuang). Yang tersisa hanya `WifiIconView` 21dp, tetap non-focusable tanpa styling pill. Logika refresh tidak berubah: `updateWifiStatus()` tetap memanggil `WifiStatus.describe()` pada resume + tick 15 detik, hanya level-nya yang dipakai untuk busur ikon (slash saat offline).

Status kejujuran: compile-verified + badging/sertifikat terverifikasi dari VM; konfirmasi visual (posisi tombol di samping quote, top bar lebih lega) menunggu retest user di TV, seperti fase sebelumnya.

---

# BUILD REPORT — Fase 4.2 (logo app, thumbnail seragam, signing fallback) — v0.4.2

Tanggal: 2026-11-01 · Status akhir: **BUILD SUCCESSFUL — APK 0.4.2 jadi, badging + sertifikat + isi resource terverifikasi.**
Konteks: tiga permintaan user setelah 0.4.1: (1) bikinin logo buat app-nya, (2) thumbnail dibikin ada background + blur supaya seragam, (3) push ke GitHub (diurus parent — fase ini menyiapkan sisi build-nya: signing fallback supaya clone publik tetap bisa build tanpa keystore privat).

## Hasil akhir

| Item | Nilai |
|---|---|
| APK (deliverable) | `~/workspace/smart-tv-launcher/MotTvLauncher-0.4.2-debug.apk` |
| Ukuran | 9.529.733 byte (±9,5 MB — naik ±0,3 MB dari 0.4.1 karena PNG ikon 5 densitas + logo nodpi 512px) |
| applicationId | `com.mottv.launcher` |
| versionName / versionCode | **0.4.2 / 6** (dari `aapt dump badging`) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 (badging) |
| Sertifikat penanda tangan | SHA-256 **dda6fa9827737d4b447a3caa470fbec0838d534679f4579a0195ae116fe096d4** — identik keystore permanen → **0.4.2 bisa diinstal menimpa 0.4.1 TANPA uninstall** |
| Sanity | `assets/quotes.json` md5 identik dengan `quotes/quotes.json` (1600905b…) — pool tidak berubah |

Perintah build hijau sekali jalan: pola offline persis fase 1–4.1 (`assembleDebug --offline --no-daemon`, 15 task dieksekusi).

## Item 1 — Logo jadi ikon launcher + banner

- Sumber: logo generate-an di `~/workspace/smart-tv-launcher/logo/` (webp 1600×1600, TV + frame atap, play emas, bg gelap). Dikonversi dengan PIL (tersedia di VM) ke PNG: `mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi` = 48/72/96/144/192px, masing-masing `ic_launcher.png` + `ic_launcher_round.png` (art sama, sesuai brief); ukuran output diverifikasi per file. Versi 512px disimpan sebagai `drawable-nodpi/ic_launcher_logo.png` untuk banner.
- Manifest: `android:icon="@mipmap/ic_launcher"` (sudah ada) + **baru** `android:roundIcon="@mipmap/ic_launcher_round"`; `android:banner` diganti dari `@drawable/tv_banner` ke **`@drawable/app_banner`** — layer-list baru: gradasi abu gelap yang sama + bitmap logo di tengah 120dp. Atribut banner/icon/roundIcon terverifikasi ada di manifest APK (aapt xmltree) dan semua resource ikon/banner ada di dalam APK (unzip -l).
- **Adaptive icon lama dibuang** (`mipmap-anydpi-v26/ic_launcher.xml` dihapus): di API 26+ (termasuk TV user, Android 14) adaptive icon akan menimpa PNG mipmap, jadi logo baru tidak akan pernah tampil kalau file itu dipertahankan. Dengan PNG densitas sebagai satu-satunya ikon, logo tampil konsisten di semua API. File `tv_banner.xml` + `ic_foreground_art.xml` + warna `ic_launcher_background` jadi tidak terpakai tapi dibiarkan di repo (tidak mengganggu build).
- **Tentang**: logo tampil di Pengaturan → Tentang — `PlaceholderActivity` dapat extra `EXTRA_SHOW_ICON`; layout `activity_message` dapat ImageView `msgIcon` (104dp, gone default) yang ditampilkan hanya untuk layar Tentang (di-set dari `SettingsActivity`).

## Item 2 — Thumbnail seragam (framed blur system)

`FitArtView` ditulis ulang sebagai SATU sistem frame untuk semua tile (Beranda, hero, Games, Semua App):

- Seluruh view di-clip ke radius konsisten **12dp** (outline provider sendiri, tidak lagi bergantung pada clip parent).
- **Backdrop sekarang selalu terisi** — salinan blur+gelap dari artwork tile itu sendiri (banner maupun ikon), CENTER_CROP memenuhi tile. Di 0.4.0 ikon masih duduk di atas gradasi brand polos (backdrop kosong), jadi grid campur dua gaya; sekarang seragam.
- **Foreground**: artwork asli FIT_CENTER, tidak pernah di-crop (aturan fase 4 tetap absolut), dengan **inset konsisten 10dp** untuk banner penuh (sebelumnya 0) dan sudut artwork dibulatkan **10dp** via outline di atas rect ber-padding. Ikon tetap perlakuan centered (pad pemanggil: Games 26dp, Semua App 18dp, hero 30dp) tapi di dalam sistem frame yang sama.
- Tile ikon di Beranda yang sebelumnya memakai ImageView polos 58dp di atas gradasi sekarang lewat `FitArtView.setCentered(icon, 30dp)` — ukuran visual ikon sama (~58dp di tile 118dp), gayanya ikut seragam. Blur tetap trik downscale ÷16 + cache per bitmap dari fase 4 (tanpa library/dependensi baru).

## Item 3 — Signing fallback untuk clone publik

Di `app/build.gradle.kts`: signingConfig "mot" sekarang **hanya dibuat & diterapkan bila** `../keystore/mot-launcher.keystore` **dan** `keystore.properties` benar-benar ada (`hasMotKey`). Tanpa keystore (clone GitHub), debug/release jatuh diam-diam ke default debug signing Gradle — clone baru tetap build & jalan. Dengan keystore hadir (workspace ini), perilakunya identik seperti sebelumnya — dibuktikan sertifikat APK final tetap dda6fa98….

## Keterbatasan fase 4.2 (jujur)

1. Semua perubahan compile-verified + isi APK terverifikasi dari VM; tampilan ikon/banner/logo Tentang dan keseragaman thumbnail di TV menunggu retest user seperti fase sebelumnya.
2. `ic_launcher_round.png` memakai art persegi yang sama (sesuai brief "same art is fine") — bukan versi mask lingkaran khusus; launcher yang meminta roundIcon akan men-mask-nya sendiri.
3. Fallback signing untuk clone SUDAH diuji terpisah: salinan bersih app-project dibangun di /tmp tanpa folder keystore di sebelahnya — BUILD SUCCESSFUL (34/34 task dari nol) dan APK-nya tertanda tangan sertifikat "Android Debug" hasil generate otomatis (5eb1ba82…), bukan kunci mot — fallback bekerja persis seperti dirancang, tanpa menyentuh keystore asli proyek.

---

# BUILD REPORT — Fase 4.3 (SSL browser jinak, blur nge-blend, skeleton shimmer) — v0.4.3

Tanggal: 2026-11-01 · Status akhir: **BUILD SUCCESSFUL — APK 0.4.3 jadi, badging + sertifikat + quotes terverifikasi.**
Konteks: tiga catatan user dari uji TV atas 0.4.2: (1) browser bawaan mati dengan error **"Chain validation failed"**, (2) thumbnail & background blur **"masih kurang ngeblend"**, (3) loading state minta **skeleton placeholder shimmer**. (Logo 0.4.2 dinyatakan user "ok sih" — tidak disentuh.) Status di bawah tetap jujur: semua *compile-verified dari VM*; konfirmasi runtime di TV menunggu instal 0.4.3 oleh user (tidak ada percobaan ADB dari VM). **Tidak ada commit/push git di fase ini** — urusan GitHub dipegang parent.

## Hasil akhir

| Item | Nilai |
|---|---|
| APK (deliverable) | `~/workspace/smart-tv-launcher/MotTvLauncher-0.4.3-debug.apk` |
| Ukuran | 9.541.237 byte (±9,5 MB — praktis sama dengan 0.4.2; **tidak ada dependensi baru**) |
| applicationId | `com.mottv.launcher` |
| versionName / versionCode | **0.4.3 / 7** (dari `aapt dump badging`) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 (badging) |
| Sertifikat penanda tangan | SHA-256 **dda6fa9827737d4b447a3caa470fbec0838d534679f4579a0195ae116fe096d4** — identik keystore permanen → **0.4.3 bisa diinstal menimpa 0.4.2 TANPA uninstall** |
| Sanity | `assets/quotes.json` md5 identik dengan `quotes/quotes.json` (**1600905b7551b553a97dd09fc0020849**) — pool tidak berubah |

Perintah build hijau sekali jalan: pola offline persis fase 1–4.2 (`assembleDebug --offline --no-daemon`, 15 task dieksekusi). Satu-satunya warning: `FLAG_IS_GAME` deprecated di GamesActivity — sudah ada sejak fase 1, tidak berpengaruh.

## Item 1 — Browser: error SSL "Chain validation failed" jadi jinak

- **WebView** (`BrowserActivity`): WebViewClient sekarang menimpa `onReceivedSslError`. **Tidak pernah auto-proceed diam-diam**: load dibatalkan (`handler.cancel()`) dan panel error in-app tampil (overlay layar penuh + kartu, semua tombol fokusable untuk remote):
  - Judul: **"⚠ Sertifikat situs tidak bisa diverifikasi"**
  - Isi: menyebut **host**-nya + penjelasan bahasa polos — rantai sertifikat situs tidak valid/tidak lengkap, ATAU tanggal & jam TV tidak akurat; halaman diblokir demi keamanan.
  - Baris petunjuk kecil: *"Kalau banyak situs gagal: cek Pengaturan TV → Tanggal & waktu → otomatis."* (petunjuk yang sama juga ditambahkan ke halaman awal/empty state browser).
  - Tombol: **[Coba Lagi]** (muat ulang URL yang gagal), **[Tetap Buka (Tidak Aman)]** (host dimasukkan ke set persetujuan **sesi ini saja** — `Collections.synchronizedSet` in-memory di companion, tidak pernah ditulis ke disk; load host itu berikutnya dalam sesi yang sama tidak ditanya lagi), **[Kembali]** (riwayat mundur, atau keluar kalau tidak ada riwayat). Back saat panel tampil = perilaku tombol Kembali.
- **Request non-WebView** (`Net`): `getJson`/`getText` sekarang membungkus kegagalan yang rantai penyebabnya mengandung `SSLHandshakeException`/`CertPathValidatorException`/`CertificateException` menjadi **`SslValidationException`** yang pesannya sudah bahasa polos + petunjuk cek tanggal & jam TV. Semua pemanggil yang menampilkan `e.message` (Hasil Pencarian, Jadwal Sholat) otomatis menampilkannya; `BolaActivity.shortError` dapat cabang khusus ("sertifikat situs tidak bisa diverifikasi — cek tanggal & jam TV (atur otomatis)"). `getBitmap` tidak diubah (kegagalan gambar memang selalu jatuh ke placeholder jujur).
- Kejujuran penyebab: error ini di TV hampir selalu salah satu dari (a) rantai sertifikat situs memang tidak lengkap, (b) jam/tanggal TV meleset sehingga sertifikat terlihat belum berlaku/kedaluwarsa. App tidak bisa membedakan keduanya dari luar — karena itu panel menyebut dua-duanya dan menawarkan pilihan sadar ke user, bukan bypass senyap.

## Item 2 — Blur nge-blend (FitArtView + TileArt)

- **Blur lebih berat** (`TileArt.blurredBackdrop`): downscale **÷24** (dulu ÷16) lalu **dua** pass upscale berfilter dengan satu downscale tambahan di tengahnya — jatuhnya halus seperti gaussian, bukan satu langkah kasar. Penggelapan diseragamkan (alpha 110). Tetap di-cache per bitmap sumber; bitmap sumber tidak pernah di-recycle.
- **Overscan backdrop**: ImageView backdrop di-scale **×1,15** sehingga tepi luar gambar blur (dan ringing paling terang/gelapnya) berada di luar frame yang ter-clip — tidak ada lagi tepi persegi yang terlihat di dalam tile.
- **Veil gradasi**: lapisan baru di antara backdrop dan artwork — gradasi radial warna keluarga tile (#26282E, ujung gradasi background app), transparan di tengah dan menggelap ke tepi/bawah, sehingga backdrop "meleleh" ke permukaan tile.
- **Bayangan lembut di bawah artwork depan**: tiga lapisan rounded-rect hitam bertumpuk (alpha 10/18/30, ekspansi 9/5,5/2,5dp, jatuh 1,5dp) digambar di `drawChild` tepat sebelum layer depan dirender — artwork terasa melayang, bukan berakhir di border keras. Aturan lama tetap absolut: inset 10dp, radius depan 10dp, foreground **tidak pernah di-crop**. Berlaku di semua pemakai FitArtView: tile Beranda, hero, Games, Semua App.

## Item 3 — Skeleton shimmer saat loading

Komponen baru `Shimmer.kt`:
- **`Shimmer`** (object): SATU `ValueAnimator` bersama untuk seluruh app (1200 ms, linear, loop), berjalan hanya selama ada `ShimmerBar` yang ter-attach; semua bar membaca `phase` yang sama sehingga sapuan sinkron; animator berhenti sendiri saat bar terakhir detach — murah untuk TV.
- **`ShimmerBar`**: satu bentuk placeholder — rounded-rect warna dasar **#3A3E47** + pita cahaya menyapu (puncak alpha ≈0,15), radius bisa diatur (999 = lingkaran/pill). Tidak focusable/clickable — skeleton tidak pernah menjebak D-pad.

Pemakaian per layar:
- **Jadwal Bola**: skeleton dibangun di kode meniru konten asli — 2 grup liga (header: lingkaran + bar nama + bar tag) × 3 baris match (lingkaran, bar tim, bar jam, bar tim, lingkaran). Tampil selama `loadDate`, hilang saat hasil/cache/error dirender.
- **Jadwal Sholat**: blok shimmer di layout — satu kartu "sholat berikutnya" (84dp) + grid **4×2** blok waktu 76dp, radius 12dp sama seperti tile asli. Saat tampil, kartu + grid asli disembunyikan (skeleton menggantikan posisinya persis); hilang saat jadwal dirender atau error.
- **Hasil Pencarian**: 4 baris skeleton (kotak thumbnail 168×94 + bar judul/snippet/domain) tampil selama fetch DuckDuckGo, hilang di semua outcome (hasil, robot-check, error).
- **Semua App & Games**: `ShimmerBar` seukuran tile ditaruh **di belakang** FitArtView di `item_app.xml`/`item_game.xml` sebagai placeholder selama artwork ter-bind (artwork PackageManager lokal memang nyaris instan — sesuai catatan brief, shimmer di sini berperan sebagai placeholder di belakang artwork; tile tanpa artwork sama sekali menyembunyikannya dan memakai fallback inisial seperti sebelumnya).

## Keterbatasan fase 4.3 (jujur)

1. Semua perubahan **compile-verified**; belum ada verifikasi runtime/emulator. Khususnya: panel SSL baru bisa dibuktikan hanya saat ada situs yang sertifikatnya gagal di TV; kualitas "nge-blend" blur dan rasa shimmer dinilai user di TV.
2. Persetujuan "Tetap Buka" bersifat per-sesi proses app — app di-force-stop / TV restart akan bertanya lagi. Itu disengaja (persetujuan permanen untuk sertifikat rusak tidak aman).
3. Shimmer memakai satu animator ValueAnimator di main thread dengan invalidate per bar — jumlah bar per layar kecil (±40 terburuk di Bola), jauh di bawah beban animasi UI biasa di TV ini.

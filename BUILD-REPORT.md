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


---

# BUILD REPORT — Fase 5 (4 fitur persetujuan user: update mandiri, menu long-press, voice search, Bola live) — v0.5.0

Tanggal: 2026-11-01 · Status akhir: **BUILD SUCCESSFUL — APK 0.5.0 jadi, badging + sertifikat + quotes terverifikasi.**
Konteks: user menyetujui 4 usulan perbaikan (dari daftar "tanpa mengubah looks major") pada 2026-11-01: (1) Cek Pembaruan dari GitHub Releases sendiri, (2) menu long-press di tile, (3) pencarian suara, (4) sentuhan live Jadwal Bola. Di tengah fase ditambah **item 5** atas permintaan user yang kesusahan mencari launcher bawaan di menu-menu TV: tombol "Jadikan Launcher Default" di Pengaturan (lihat sub-bab Item 5 di akhir seksi ini). APK 0.5.0 di-build ulang sekali untuk item 5 — versi & sertifikat tidak berubah, jadi tetap menimpa bersih. **Tidak ada perubahan layout mayor** — semua halaman yang sama, bahasa visual yang sama; yang berubah: satu item menu baru di Pengaturan, satu layar update baru bergaya sama, label mic di Pencarian jadi tombol beneran, dan teks pill Bola bisa memanjang sesekali. Status di bawah tetap jujur: semua *compile-verified dari VM*; perilaku runtime di TV menunggu instal 0.5.0 oleh user (tidak ada percobaan ADB dari VM). **Tidak ada commit/push git di fase ini** — urusan GitHub dipegang parent.

## Hasil akhir

| Item | Nilai |
|---|---|
| APK (deliverable) | `~/workspace/smart-tv-launcher/MotTvLauncher-0.5.0-debug.apk` |
| Ukuran | 9.720.269 byte (±9,7 MB — angka dari rebuild final fase ini setelah item 5 masuk; **tidak ada dependensi pihak ketiga baru**) |
| applicationId | `com.mottv.launcher` |
| versionName / versionCode | **0.5.0 / 8** (dari `aapt dump badging`) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 (badging) |
| Izin baru di badging | `REQUEST_INSTALL_PACKAGES` (update mandiri) |
| Komponen baru terverifikasi di manifest APK | provider `androidx.core.content.FileProvider` (authority `com.mottv.launcher.fileprovider`), activity `UpdateActivity` |
| Sertifikat penanda tangan | SHA-256 **dda6fa9827737d4b447a3caa470fbec0838d534679f4579a0195ae116fe096d4** — identik keystore permanen → **0.5.0 bisa diinstal menimpa 0.4.3 TANPA uninstall** |
| Sanity | `assets/quotes.json` md5 identik dengan `quotes/quotes.json` (**1600905b7551b553a97dd09fc0020849**) — pool tidak berubah |

Perintah build: pola offline persis fase 1–4.3 (`assembleDebug --offline --no-daemon`). Satu-satunya insiden: pola `continue` di dalam lambda inline (`ifBlank { continue }`) ditolak compiler ("break continue in inline lambdas is experimental") → ditulis ulang eksplisit, lalu build hijau.

## Item 1 — Cek Pembaruan (update mandiri dari GitHub Releases sendiri)

- **Menu baru di Pengaturan**: "Cek Pembaruan" (di antara Bahasa dan Pengaturan TV), hint-nya menyebut versi terinstal saat ini (dibaca runtime dari PackageManager, bukan string basi). Membuka layar baru **`UpdateActivity`** — layout sendiri tapi bahasa visual yang sama persis (background + judul besar + teks status center + tombol pill emas).
- **Pemeriksaan**: GET `https://api.github.com/repos/isman17/mot-tv-launcher/releases/latest` via helper `Net` yang ada (browser UA). `tag_name` (leading "v" dibuang) dibandingkan per-segmen numerik dengan versionName terinstal:
  - Tidak lebih baru → "Sudah versi terbaru" + info tag terakhir.
  - Lebih baru tapi rilis **tidak memuat aset .apk** → dinyatakan apa adanya, tidak ada tombol pasang palsu.
  - Lebih baru + ada .apk → kartu **cuplikan release notes** (maks ±600 karakter, maxLines 8) + tombol **[Unduh & Pasang]**.
- **Degradasi jujur (penting)**: repo ini (saat fase dikerjakan) berstatus **PRIVATE** → API Releases anonim menjawab 404 (GitHub menyamarkan repo privat sebagai "not found"). Setiap kegagalan (404/error/jaringan/SSL) menampilkan persis sikap yang dirancang: **"Belum bisa memeriksa pembaruan (repo privat / tidak ada koneksi). Versi kamu: X."** + kode alasan singkat dalam kurung siku — tidak pernah crash, tidak pernah pura-pura ada/tidak ada update. Begitu repo dibuat publik, fitur ini langsung bekerja **tanpa perubahan app** (endpoint & parsing yang sama). *(Catatan: selama fase ini berjalan user mengubah repo jadi PUBLIC dan parent memverifikasi API-nya menjawab 200 dari VM — jadi jalur suksesnya sekarang terbuka; verifikasi di TV tetap menunggu retest user seperti biasa.)*
- **Unduh**: aset .apk (aset pertama yang namanya berakhiran `.apk`) di-stream ke `cacheDir/updates/` via HttpURLConnection (UA browser, redirect diikuti, timeout 15s/30s), progres tampil sebagai **persen** bila server mengirim panjang konten, atau ukuran terunduh bila tidak. Kegagalan unduh → pesan + alasan, tombol kembali ke "Unduh & Pasang". SSL failure diterjemahkan ke `SslValidationException` bahasa polos yang sama seperti fase 4.3.
- **Pasang**: URI dari **FileProvider** (`androidx.core.content.FileProvider` — terverifikasi ada di dalam `androidx.core` 1.13.1 yang sudah ditarik core-ktx; `classes.jar`-nya diperiksa langsung di maven-local sebelum coding, jadi fitur ini TIDAK ter-block) + intent `ACTION_VIEW` type `application/vnd.android.package-archive` + `FLAG_GRANT_READ_URI_PERMISSION` + `FLAG_ACTIVITY_NEW_TASK`. `res/xml/file_paths.xml` hanya membuka `cache-path updates/` — tidak ada path lain yang dishare. Manifest menambah izin `REQUEST_INSTALL_PACKAGES`.
- **Izin instal belum diberikan**: sebelum installer dibuka, app memeriksa `packageManager.canRequestPackageInstalls()`. Kalau belum → state "Beri Izin Instal": teks menjelaskan kenapa, tombol membuka `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` untuk paket app ini persis; sepulang dari sana (onResume) state otomatis jadi **"Pasang Sekarang"** bila izin sudah diberikan dan file unduhan masih ada. Kalau halaman izinnya pun tidak bisa dibuka → toast berisi jalur manual lengkapnya. Unduhan yang selesai dan izin sudah ada → installer langsung dibuka + tombol jadi "Pasang Sekarang" buat coba lagi.

## Item 2 — Menu long-press di tile (Buka / Info / Copot)

- Helper bersama **`AppActions`**: `bind(view, activity, label, packageName, open)` memasang `setOnLongClickListener` (return true — klik normal tidak ikut terpicu) dan menyimpan spec menu di tag view (`R.id.app_menu_action`, ids.xml baru); `unbind` untuk keamanan rebind RecyclerView.
- **Dialog**: `AlertDialog` bawaan AppCompat (fokus D-pad natural), judul = nama app asli, item:
  - **Buka** — menjalankan aksi klik normal tile itu persis.
  - **Info Aplikasi** — `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` untuk paketnya.
  - **Copot Pemasangan** — `Intent.ACTION_DELETE` `package:` (sistem yang memutuskan — app sistem akan ditawari disable oleh sistem sendiri). Item ini **disembunyikan** untuk paket sendiri (`com.mottv.launcher`) dan Play Store (`com.android.vending`).
- **Cakupan**: tile Beranda yang berbasis paket nyata & terinstal (tile Browser/Games adalah modul in-app tanpa paket, tile belum-terinstal tidak punya apa-apa buat diapa-apakan — keduanya sengaja tidak diberi menu), semua kartu grid Games (tile Play Store di Games di-bind ke paket vending → dapat Buka + Info, tanpa Copot), dan semua kartu grid Semua App (paket sendiri memang tidak pernah terdaftar di sana).
- **Tombol MENU remote**: ketiga activity meneruskan `dispatchKeyEvent` ke `AppActions.handleMenuKey` — KEYCODE_MENU (ACTION_UP) pada view berfokus (atau induknya) yang punya menu ter-bind akan membuka dialog yang sama.

## Item 3 — Pencarian suara di Pencarian

- Label statis lama "Mic — tahan tombol suara di remote" di header Pencarian diganti **tombol fokusable "🎤 Suara"** di posisi & gaya pill yang sama (di samping tombol Cari).
- Tap → `Intent(ACTION_RECOGNIZE_SPEECH)` dengan `EXTRA_LANGUAGE_MODEL=FREE_FORM`, `EXTRA_LANGUAGE="id-ID"`, prompt "Bicara sekarang…", lewat activity-result launcher. Hasil `RESULT_OK`: hipotesis teratas masuk ke kolom query dan **pencarian langsung dijalankan** (jalur `doSearch()` yang sama — URL tetap dibuka di browser, kata tetap ke Hasil).
- **Jujur saat tidak tersedia**: `resolveActivity` null atau `ActivityNotFoundException` → toast **"Pencarian suara tidak tersedia di TV ini"**; keyboard on-screen tidak terganggu. Tidak ada izin baru (intent terdelegasi ke recognizer sistem). Tombol **KEYCODE_SEARCH** di remote, saat di layar ini, memicu hal yang sama.

## Item 4 — Bola live: auto-refresh + pill pertandingan berikutnya

- **Auto-refresh 60 detik** (`BolaActivity`): timer Handler berjalan hanya saat activity di-resume (dihentikan di onPause/onDestroy). Tick hanya bekerja bila tanggal terpilih adalah **hari ini**. Refresh-nya **diam-diam**: rantai hybrid & urutan sekuensial yang sama seperti muat manual, tapi tanpa skeleton, tanpa membersihkan layar, tanpa teks error. Kegagalan **per-liga mempertahankan data lama liga itu** (429 sesaat tidak boleh mengosongkan skor live yang sedang tampil); kegagalan total tidak mengubah apa pun di layar. Keberhasilan penuh menulis cache persisten seperti muat manual. Muat manual yang mulai di tengah jalan menang (snapshot `loadSeq`). Render ulang **mempertahankan posisi scroll** (ScrollView di-restore setelah render) dan **fokus** (anak `groupsContainer` pada indeks yang sama di-fokus ulang bila sebelumnya ada yang fokus) sebisa mungkin.
- **Pill Bola di Beranda**: pada `onResume`, pill bisa menjadi **"⚽ Bola · {Home} {HH:mm}"** bila ada kickoff dalam 24 jam ke depan (basis waktu WIB, konsisten dengan halaman Bola); tanpa kandidat pill tetap label polosnya persis seperti sekarang ("Jadwal Bola" — label diam tidak diubah sama sekali). Nama tim dipadatkan ≤10 karakter (kata pertama bila enak dibaca: "Persib Bandung" → "Persib"; selain itu potong 10). Sumber berurutan dari yang termurah:
  1. **Cache hari persisten milik BolaActivity** (SharedPreferences `bola_cache`, hari ini + besok) — data asli yang memang sudah pernah dimuat user; hanya match berstatus `NS`, tidak ditunda, kickoff di jendela (sekarang, +24 jam).
  2. **Cache kecil milik Beranda sendiri** (`home_bola`: pick terakhir + waktu fetch).
  3. Bila semua kosong atau **lebih tua dari 30 menit** → **fetch background** ringan memakai rantai hybrid yang sama (Liga Indonesia via TheSportsDB, lima liga besar via ESPN keyless, saling fallback saat error), hari ini dulu — ketemu match Indonesia langsung berhenti (ia memang prioritas). Hasilnya (satu pick terbaik saja) disimpan di cache Beranda sendiri — **sengaja tidak** ditulis ke cache hari BolaActivity karena format hari penuhnya tidak direproduksi lookup ringan ini. Semua kegagalan senyap → pill tetap polos.
  - **Prioritas pick**: match Liga Indonesia (4790) mengalahkan kickoff lebih awal dari liga lain; di dalam kelompok yang sama, kickoff paling awal yang menang.

## Keterbatasan fase 5 (jujur)

1. Semua perubahan **compile-verified + isi APK terverifikasi dari VM**; belum ada verifikasi runtime/emulator (tidak ada perangkat/ADB dari VM, sama seperti fase sebelumnya). Yang menunggu retest user di TV: dialog long-press & tombol MENU, recognizer suara bawaan TV/remote, auto-refresh yang benar-benar menggerakkan skor live, pill yang terisi dari jadwal asli, dan seluruh alur update (cek → unduh → izin → installer) ujung ke ujung.
2. Alur Cek Pembaruan jalur suksesnya bergantung repo publik — user mengubah repo jadi PUBLIC di tengah fase ini (parent memverifikasi API 200 dari VM); bila repo dibuat privat lagi, app kembali ke pesan degradasi jujur di atas, bukan error.
3. Pemeriksaan update hanya memeriksa rilis **terbaru** (`/releases/latest`) dan memilih aset `.apk` pertama di rilis itu — sesuai struktur rilis proyek ini (satu APK per rilis).
4. Auto-refresh mempertahankan data lama untuk liga yang gagal, jadi skor bisa tertinggal satu siklus (60 dtk) saat sumbernya batuk — ditampilkan apa adanya tanpa label khusus, konsisten dengan sikap cache fase 3–4.
5. Pill Beranda memakai cache hari Bola hanya bila user pernah membuka Jadwal Bola untuk tanggal itu; sebelum itu ia bergantung pada lookup background Beranda sendiri yang berjalan paling sering sekali per 30 menit dan bisa saja tidak menemukan apa-apa pada hari tanpa pertandingan — pill polos pada hari kosong adalah jawaban jujur, bukan error.

## Item 5 (tambahan di tengah fase 5) — tombol "Jadikan Launcher Default"

- **Penempatan**: item menu baru di Pengaturan, persis **di bawah "Cek Pembaruan"** (dekat bagian atas daftar), gaya `SettingsMenuItem` yang sama persis — tidak ada elemen visual baru. Label: **"Jadikan Launcher Default"**; teks penjelas di panel kanan: *"Pilih Mot TV Launcher sebagai aplikasi Beranda — tombol ini membuka langsung layar "Aplikasi Beranda" bawaan sistem, tinggal pilih Mot TV Launcher di sana."*
- **Perilaku**: tap → `Intent(Settings.ACTION_HOME_SETTINGS)` + `FLAG_ACTIVITY_NEW_TASK` — layar pilihan **Home app bawaan sistem** terbuka langsung, user tinggal memilih Mot TV Launcher di sana. Ini menghilangkan friksi yang memicu item ini: user tidak perlu lagi berburu entri launcher bawaan di dalam menu Pengaturan TV yang berlapis-lapis.
- **Fallback jujur berjenjang**: `ActivityNotFoundException` dari intent utama → jatuh ke `Intent(Settings.ACTION_SETTINGS)` (Pengaturan sistem umum); kalau itu pun gagal → toast panjang berisi jalur manual lengkapnya (Pengaturan TV → Aplikasi → Aplikasi default → Aplikasi Beranda). Tidak ada perilaku lain yang diubah; app tidak menyentuh status default apa pun sendiri — keputusan tetap dibuat user di layar sistem.
- **Verifikasi rebuild**: BUILD SUCCESSFUL; badging tetap **0.5.0 / vc8 / minSdk 26**; sertifikat tetap **dda6fa98…** (menimpa 0.4.3 — dan build 0.5.0 sebelumnya — tanpa uninstall); md5 `quotes.json` tetap **1600905b…**. Ukuran APK final 9.720.269 byte (tabel Hasil akhir di atas sudah memakai angka rebuild ini). Perilaku runtime (layar Home-settings di TCL user benar-benar terdaftar & terbuka) tetap menunggu retest user di TV, seperti item fase 5 lainnya.


---

# BUILD REPORT — Fase 6 (override tombol Home ala Projectivy) — v0.6.0

Tanggal: 2026-11-01 · Status akhir: **BUILD SUCCESSFUL — APK 0.6.0 jadi, badging + sertifikat + quotes + manifest layanan terverifikasi.**
Konteks: di TV user (TCL Google TV, Android 14) rute default-home biasa **gagal secara struktural** — launcherx bawaan memegang prioritas Home tertinggi, chooser tombol Home tidak pernah muncul, dan pilihan lewat ACTION_HOME_SETTINGS (tombol fase 5) tidak lengket (terverifikasi dari percobaan 0.5.0 di TV). User memilih rute override aksesibilitas ala Projectivy di chat utama. Rute ini **sepenuhnya bisa dibatalkan user** dari Setelan TV. Batasan fase ini ditegakkan persis: **tidak ada paket yang di-disable, tidak ada trik ADB** — dan "looks" tidak berubah (satu item menu Pengaturan baru + satu layar baru bergaya sama persis seperti layar Cek Pembaruan). Status di bawah tetap jujur: semua *compile-verified + isi APK terverifikasi dari VM*; perilaku runtime di TV menunggu instal 0.6.0 oleh user. **Tidak ada commit/push git di fase ini** — urusan GitHub dipegang parent.

## Hasil akhir

| Item | Nilai |
|---|---|
| APK (deliverable) | `~/workspace/smart-tv-launcher/MotTvLauncher-0.6.0-debug.apk` |
| Ukuran | 9.576.055 byte (±9,6 MB — praktis sama dengan 0.5.0; **tidak ada dependensi pihak ketiga baru**) |
| applicationId | `com.mottv.launcher` |
| versionName / versionCode | **0.6.0 / 9** (dari `aapt dump badging`) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 (badging) |
| Sertifikat penanda tangan | SHA-256 **dda6fa9827737d4b447a3caa470fbec0838d534679f4579a0195ae116fe096d4** — identik keystore permanen → **0.6.0 bisa diinstal menimpa 0.5.0 TANPA uninstall** |
| Komponen baru terverifikasi di manifest APK | service `com.mottv.launcher.HomeInterceptService` (permission `BIND_ACCESSIBILITY_SERVICE`, exported, intent-filter `android.accessibilityservice.AccessibilityService`, meta-data → config), activity `HomeOverrideActivity`, file `res/xml/home_accessibility_config.xml` di dalam APK |
| Sanity | `assets/quotes.json` md5 identik dengan `quotes/quotes.json` (**1600905b7551b553a97dd09fc0020849**) — pool tidak berubah |

Perintah build hijau sekali jalan: pola offline persis fase 1–5 (`assembleDebug --offline --no-daemon`, 15 task dieksekusi, tanpa loop fetch — tidak ada dependensi baru).

## Cara kerja override

**`HomeInterceptService : AccessibilityService`** — dua jalur, keduanya tunduk pada saklar master yang sama:

1. **Jalur utama — filter tombol** (`onKeyEvent`): KEYCODE_HOME (ACTION_DOWN) dikonsumsi (return true) dan dijawab dengan membuka `MainActivity` sendiri (intent eksplisit per-komponen, `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_REORDER_TO_FRONT` — MainActivity memang singleTask, jadi ia naik ke depan, bukan ditumpuk). Tombol lain selalu diteruskan ke `super`.
2. **Jaring pengaman — event jendela** (`onAccessibilityEvent`, hanya `TYPE_WINDOW_STATE_CHANGED`): bila paket yang tampil di depan adalah **`com.google.android.apps.tv.launcherx`** atau **`com.google.android.tungsten.setupwraith`** (keduanya app khusus-Home, jadi kemunculannya = user sedang di home bawaan), bounce ke MainActivity. Jalur ini ada karena sebagian build Google TV tidak pernah mengantarkan KEYCODE_HOME ke filter tombol. **Debounce keras**: maks 1 bounce per **3 detik**; tidak pernah bounce bila event tiba **<1,5 detik** setelah kita sendiri membuka MainActivity (timestamp dicatat *sebelum* startActivity, jadi event balapan dari peluncuran sendiri dikenali); tidak pernah bounce untuk paket sendiri. `onServiceConnected` juga menyemai jendela tenggang 1,5 detik yang sama supaya event basi tidak memantulkan user yang masih duduk di layar aksesibilitas. `onInterrupt` no-op.

**Keamanan & kejujuran layanan**: seluruh badan handler dibungkus try/catch — layanan aksesibilitas yang crash-loop tidak bisa diterima, jadi kegagalan apa pun = diam (Home bawaan tetap bekerja seperti biasa). Konfigurasi (`res/xml/home_accessibility_config.xml`) sengaja minim kuasa: `canRequestFilterKeyEvents="true"`, **`canRetrieveWindowContent="false"`** (layanan secara teknis **tidak bisa** membaca isi layar), eventTypes hanya `typeWindowStateChanged`, flag hanya `flagRequestFilterKeyEvents`, notificationTimeout 100. Label + deskripsi adalah **resource string** (`home_service_label` "Mot TV — Tombol Home", `home_service_desc`) yang tampil di layar pengaktifan sistem — isinya menyatakan polos apa yang diawasi dan apa yang tidak.

**Saklar master** (`HomeOverrideStore`): boolean `home_override` di SharedPreferences, **default ON** (user yang repot-repot mengaktifkan layanan memang datang untuk ini), dibaca segar oleh layanan pada **setiap** event — saklar di Pengaturan berlaku seketika tanpa restart layanan. OFF = semua event lewat utuh tak tersentuh.

## UI Pengaturan — "Tombol Home Langsung ke Sini"

- Item menu baru di Pengaturan, persis **di atas** "Jadikan Launcher Default" (item lama itu **dipertahankan** sebagai alternatif), membuka layar baru **`HomeOverrideActivity`** — bahasa visual identik dengan layar Cek Pembaruan fase 5 (background sama, judul besar, teks emas, kartu penjelasan, tombol pill).
- **Status dihitung live setiap onResume** (termasuk sepulang dari Setelan TV): layanan dianggap aktif bila komponen kita muncul di `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` (dibandingkan per-komponen hasil unflatten) **atau** di daftar `AccessibilityManager.getEnabledAccessibilityServiceList` — dua cara independen karena build TV bervariasi soal kecepatan refleksinya. Tiga teks status persis: "Status: AKTIF — tombol Home membuka launcher ini" (aktif + saklar ON), "Status: layanan aktif tapi override MATI" (aktif + saklar OFF), "Status: BELUM AKTIF".
- **Tombol**: [Aktifkan di Setelan TV] → `Settings.ACTION_ACCESSIBILITY_SETTINGS` (fallback `ACTION_SETTINGS`, lalu toast jalur manual bila keduanya gagal — Android memang hanya mengizinkan USER mengaktifkan layanan aksesibilitas, app tidak bisa melakukannya sendiri); [Override: ON/OFF] → membalik saklar master di tempat.
- **Teks penjelasan** di kartu, polos dan apa adanya: Google TV tidak mengizinkan cara biasa; cara ini memakai layanan aksesibilitas yang HANYA mengawasi tombol Home lalu membuka launcher ini — tidak membaca isi layar; aktifkan sekali di Setelan → Aksesibilitas → "Mot TV — Tombol Home"; bisa dimatikan kapan saja dari menu yang sama.

## Keterbatasan fase 6 (jujur)

1. Semua perubahan **compile-verified + isi APK terverifikasi dari VM**; belum ada verifikasi runtime/emulator. Yang menunggu retest user di TV: apakah TCL ini mengantarkan KEYCODE_HOME ke filter (jalur utama) atau hanya jaring pengaman yang bekerja, rasa debounce-nya saat Home ditekan dari dalam app lain, dan apakah layar aksesibilitas TCL menampilkan layanan kita dengan label/deskripsi di atas.
2. Bounce jaring pengaman bereaksi terhadap *setiap* window-state change dari paket home bawaan — termasuk permukaan transien milik launcherx (mis. overlay pencarian suaranya). Debounce 3 detik membatasi gangguannya, dan tombol Home di permukaan itu memang seharusnya membawa user ke sini; tapi bila di TV ternyata ada permukaan launcherx yang user pakai secara sah (mis. panel notifikasi bawaan), itu kandidat penyesuaian fase berikutnya berdasarkan laporan user — bukan ditebak sekarang.
3. Layanan aksesibilitas bisa dimatikan sistem/OEM saat app di-force-stop atau sesudah update besar TV; status di layar Pengaturan akan menampilkannya jujur sebagai BELUM AKTIF dan jalurnya tinggal diaktifkan ulang sekali.
4. Saklar master default ON berarti perilaku override langsung hidup begitu layanan diaktifkan user di Setelan — itu memang maksudnya; mematikannya tidak butuh membuka Setelan TV (cukup dari layar yang sama di Pengaturan launcher).

---

# BUILD REPORT — Fase 6.1 (fokus D-pad terlihat di SEMUA layar + audit scroll) — v0.6.1

Tanggal: 2026-11-01 · Status akhir: **BUILD SUCCESSFUL — APK 0.6.1 jadi, badging + sertifikat + quotes terverifikasi.**
Konteks: dua laporan user atas 0.6.0 di TV asli: (1) tombol-tombol **tidak punya pembeda visual saat fokus** pas navigasi remote atas/bawah — paling parah di layar Cek Pembaruan pas mau update; (2) **list Pengaturan tidak bisa scroll** — item terbawah tidak terjangkau di TV 768p. Status di bawah jujur seperti biasa: semua *compile-verified dari VM*; konfirmasi visual di TV menunggu retest user. Tidak ada dependensi baru; tidak ada git ops di fase ini (parent yang pegang).

## Hasil akhir

| Item | Nilai |
|---|---|
| APK (deliverable) | `~/workspace/smart-tv-launcher/MotTvLauncher-0.6.1-debug.apk` |
| Ukuran | 9.577.147 byte (±9,6 MB — praktis sama dengan 0.6.0) |
| applicationId | `com.mottv.launcher` |
| versionName / versionCode | **0.6.1 / 10** (dari `aapt dump badging`) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 (badging) |
| Sertifikat penanda tangan | SHA-256 **dda6fa9827737d4b447a3caa470fbec0838d534679f4579a0195ae116fe096d4** — identik keystore permanen → **0.6.1 bisa diinstal menimpa 0.6.0 TANPA uninstall** |
| Sanity | `assets/quotes.json` md5 identik dengan `quotes/quotes.json` (**1600905b7551b553a97dd09fc0020849**) — pool tidak berubah |

Perintah build hijau sekali jalan: pola offline persis fase 1–6 (`assembleDebug --offline --no-daemon`, 17 task dieksekusi). Satu-satunya warning: `FLAG_IS_GAME` deprecated di GamesActivity — sudah ada sejak fase 1, tidak berpengaruh.

## Diagnosis (kenapa fokus tidak terlihat)

Pola lama app ini: sebagian kontrol menukar background lewat `OnFocusChangeListener` manual ke `pill_on` (putih solid), tapi polanya **tidak pernah diterapkan ke layar-layar baru fase 5–6** — `UpdateActivity` dan `HomeOverrideActivity` hanya memasang click listener, background statis `pill_gold`/`pill_bg`, jadi fokus benar-benar tidak terlihat di sana (persis layar yang dilaporkan user). Panel SSL punya swap manual sendiri, dialog long-press memakai list bawaan AlertDialog (tema AppCompat terang → highlight fokus pucat), dan baris Pengaturan memakai shape polos. Fase ini mengganti SEMUA pola itu dengan **satu sistem bersama**, bukan tambal per layar.

## Sistem fokus bersama (baru)

**Selector drawable** (`res/drawable/mot_focus_*.xml`) — state default-nya **identik piksel** dengan tampang lama (aturan brief: unfocused tidak berubah), state fokus menambah fill lebih terang + stroke 3dp, state pressed ada sendiri:

| Drawable | Default (= tampang lama) | Fokus | Pressed |
|---|---|---|---|
| `mot_focus_pill` | `pill_bg` (translusen, radius 999) | fill #40FFFFFF + stroke 3dp putih | gradasi emas |
| `mot_focus_gold` | `pill_gold` (gradasi emas) | gradasi sama + stroke 3dp putih | gradasi emas lebih dalam |
| `mot_focus_white` | `pill_on` (putih solid) | putih + stroke 3dp **emas** | abu terang |
| `mot_focus_row` | `menu_item` (#1AFFFFFF, radius 11) | fill #36FFFFFF + stroke 3dp putih | emas translusen |

Satu-satunya deviasi sadar: `mot_focus_white` memakai ring **emas**, bukan putih — stroke putih di atas fill putih tidak akan terlihat; ring emas justru paling kontras di atas putih. Dipakai untuk kontrol yang default-nya memang putih: pill tanggal terpilih & chip terpilih (Bola/Search/Hasil) dan tombol Putar/Jeda player.

**`FocusFx.kt`** — fungsi `View.focusFx(scale)` memasang animasi scale (120 ms) + `translationZ` saat fokus berubah. Sengaja **bukan** `bringToFront()`: itu mengurut ulang anak LinearLayout (baris bisa tertukar posisi); translationZ hanya mengubah urutan gambar. Skala dipakai di tombol/pill/chip/key/tombol player (1.04–1.08); **baris full-width tidak diskala** (akan meleber melewati tepi layar) — sinyal fokusnya murni stroke + brighten dari selector. Konsekuensi arsitektural: semua listener swap-background manual lama **dihapus** (slot listener tunggal sekarang milik FocusFx di kontrol yang diskala; sisanya murni drawable).

## Sweep per layar (kontrol → perlakuan)

- **UpdateActivity (Cek Pembaruan)** — titik nyeri yang dilaporkan: `updateAction` → `mot_focus_gold` + focusFx 1.05, `updateBack` → `mot_focus_pill` + focusFx. Listener terpasang sekali di onCreate dan **bertahan di semua state tombol** (Periksa Pembaruan / Memeriksa… / Periksa Lagi / Unduh & Pasang / Beri Izin Instal / Pasang Sekarang) karena yang berubah hanya teks & enabled. Fokus awal sudah ada (`requestFocus`) sejak fase 5.
- **HomeOverrideActivity** — ketiga kontrol (Aktifkan di Setelan TV = gold selector, Override ON/OFF + Kembali = pill selector) + focusFx 1.05. Fokus awal sudah ada di tombol Aktifkan.
- **Pengaturan (SettingsActivity)** — style `SettingsMenuItem` sekarang menunjuk `mot_focus_row`; listener di `menuItem()` disisakan **hanya** untuk mengganti teks hint kanan (tidak lagi menyentuh background/warna teks — teks emas "Pengaturan TV (Android) ↗" tetap emas saat fokus, kontras cukup di atas fill fokus yang translusen).
- **PlaceholderActivity / Tentang / pesan** — `msgButton` → `mot_focus_gold` + focusFx.
- **BackgroundActivity** — baris opsi & mode memakai style `SettingsOptionRow` → otomatis `mot_focus_row`; fungsi `focusStyle()` lama dihapus (penanda "✓" tetap cara menandai pilihan aktif). **Slider kegelapan**: container-nya sekarang memakai `mot_focus_row` sebagai background SeekBar (+ padding) → fokus pada slider menampakan ring yang sama.
- **BrowserActivity** — tombol "Buka" → `mot_focus_gold` + focusFx; kolom URL → `mot_focus_row` (stroke saat fokus). **Panel SSL**: `styleSslButton` ditulis ulang — background selector bersama (Retry = gold, Tetap Buka/Kembali = pill) + focusFx; fokus awal ke "Coba Lagi" sudah ada sejak 4.3.
- **SearchActivity** — backBtn/micBtn/searchBtn: selector di layout + focusFx; fungsi `styleFocusablePill` dihapus. **Chip kategori**: terpilih = `mot_focus_white` (teks gelap statis), tidak terpilih = `mot_focus_pill`, + focusFx. **Keyboard on-screen**: huruf = `mot_focus_row`, tombol "Cari" emas = `mot_focus_gold`, + focusFx 1.06 (sebelumnya swap manual). **Recent searches**: pill → `mot_focus_pill` + focusFx.
- **ResultsActivity** — backBtn + chip kategori pola sama seperti Search; **kartu hasil**: background di `item_result.xml` diganti `mot_focus_row`, listener swap di adapter dihapus (sekalian menghilangkan bug laten rebind RecyclerView yang menukar background). Baris full-width → tanpa skala.
- **BolaActivity** — pill tanggal: terpilih = `mot_focus_white`, tidak = `mot_focus_pill` (ditentukan `refreshDatePills()`; fokus tidak lagi mewarnai teks); tombol kalender (datePick) → `mot_focus_gold` + focusFx; backBtn → pill selector + focusFx; **chip liga**: terpilih = white selector, tidak = pill selector, + focusFx; **baris match**: background `mot_focus_row` (menggantikan swap manual ke `tile_focus_border`), tanpa skala.
- **CalendarActivity** — 5 tombol nav → `mot_focus_pill` + focusFx; **sel tanggal**: terpilih = `mot_focus_gold` (sebelumnya sel terpilih **tidak berubah sama sekali** saat fokus — celah nyata yang sekarang tertutup oleh stroke putih), hari ini & hari biasa = `mot_focus_row` (teks emas hari ini dipertahankan), + focusFx 1.08.
- **SholatActivity** — "Ganti Kota" → `mot_focus_gold` + focusFx (teks gelap statis dari layout); **baris daftar kota** → `mot_focus_row` di adapter, teks tetap putih, tanpa skala.
- **PlayerActivity** — tombol ⏮/−10/+10/⏭/subtitel → `mot_focus_pill` + focusFx 1.06 (helper `wireButton`); Putar/Jeda → `mot_focus_white` (unfocused tetap putih seperti lama; fokus = ring emas); "Buka di Browser" → `mot_focus_gold`; tombol tutup error → pill selector. Semua dari layout, listener swap dihapus.
- **Beranda (MainActivity)** — 4 pill atas (Bola/Sholat/Pengaturan/Semua App) → `mot_focus_pill` + focusFx, menggantikan swap manual di `setupPill`.
- **Dialog long-press (AppActions)** — ditulis ulang: bukan lagi `AlertDialog.setItems` (list tema terang, highlight fokus nyaris tak terlihat di TV). Sekarang panel custom gelap (`mot_dialog_bg` dipasang sebagai background window dialog setelah `show()`): judul sendiri + satu baris fokusable per aksi, tiap baris `mot_focus_row` + focusFx 1.01; baris pertama langsung `requestFocus()`. Isi & syarat menu tidak berubah (Copot tetap disembunyikan untuk paket sendiri & Play Store).

## Pengecualian yang disengaja (hasil grep audit akhir)

- **Tile Beranda / Games / Semua App** — sudah punya perlakuan fokus sendiri yang bagus (foreground `tile_focus_border` + scale 1.05–1.06, terverifikasi di kode) → tidak diubah, sesuai brief.
- **SeekBar player** — memang `focusable="false"` **by design**: ia indikator progres murni; seek dilakukan tombol ±10 yang fokusable (kontrol tampil) atau tombol kiri/kanan remote (kontrol tersembunyi). Menjadikannya fokusable akan mengubah skema kontrol player — di luar scope.
- **Label dekoratif non-fokusable** tetap memakai drawable lama (default-nya identik): pill "Beranda" (indikator halaman aktif) & "OK — Buka" di Beranda, `countPill` di Games/Semua App, `selectedLabel` di Kalender, kartu catatan rilis (Update) & kartu penjelasan (HomeOverride), pill platform di dalam baris match Bola. Tidak satu pun bisa menerima fokus, jadi tidak ada yang perlu dibedakan.
- **Kolom teks (EditText)** memakai `mot_focus_row` — stroke fokus membantu navigasi D-pad; kursor teks tetap indikator utama saat mengetik.

## Scroll audit (viewport desain: 1280×720 logis / TV 768p)

| Layar | Status sebelum | Perlakuan 6.1 |
|---|---|---|
| Pengaturan (menu utama) | **TIDAK scrollable** — 8 baris + hint; item bawah (Tentang, Pengaturan TV) terpotong/tidak terjangkau di 720p | **Dibungkus ScrollView** (fillViewport, tanpa scrollbar): kolom menu 330dp jadi area scroll, judul tetap di luar; padding bawah 16dp di dalam konten supaya baris terakhir tidak mepet tepi. Baris fokusable → ScrollView menggeser otomatis sampai baris fokus tampil penuh |
| Cek Pembaruan (Update) | Konten ter-center statis; kartu catatan rilis (maks 8 baris) + status panjang bisa overflow | **Dibungkus ScrollView** fillViewport — konten pendek tetap ter-center persis seperti sebelumnya; konten tinggi scroll mengikuti fokus; padding bawah 32dp |
| Tombol Home (HomeOverride) | Statis; kartu penjelasan panjang + status + 3 tombol mepet batas 720p | **Dibungkus ScrollView** fillViewport, pola sama seperti Update |
| Placeholder (Tentang/Bahasa/Lokasi Sholat) | Statis; body teks arbitrer (Tentang cukup panjang) bisa lari dari layar | **Dibungkus ScrollView** fillViewport di level layout bersama (`activity_message`) → semua layar Placeholder ikut sembuh |
| Background | Statis; total konten (5 opsi + slider + mode + catatan) ≈ 560dp — muat, tapi mepet | **Dibungkus ScrollView** fillViewport untuk keamanan; padding bawah dinaikkan ke 38dp |
| Jadwal Bola | Sudah aman — grup match di dalam ScrollView; chip RecyclerView horizontal | Diverifikasi, tidak diubah |
| Jadwal Sholat | Sudah aman — ScrollView sejak fase 3 (anti-kepotong); picker kota RecyclerView | Diverifikasi, tidak diubah |
| Hasil Pencarian | Sudah aman — RecyclerView | Diverifikasi, tidak diubah |
| Games / Semua App | Sudah aman — RecyclerView grid | Diverifikasi, tidak diubah |
| Pencarian | Layout tetap, total konten terukur ≈ 470dp < 720p (header + input + chip + keyboard 4 baris + recents) | Diverifikasi muat, tidak diubah |
| Kalender Bola | Layout tetap, grid bulan terburuk 6 baris ≈ di bawah 720p bersama header/nav | Diverifikasi muat, tidak diubah |
| Beranda | Layout tetap yang memang didesain per-viewport TV (sudah diuji user di semua fase) | Tidak diubah |
| Browser | WebView mengisi sisa layar & scroll sendiri; kartu panel SSL ≈ 320dp | Diverifikasi aman, tidak diubah |
| Player | Overlay di atas video fullscreen — tidak ada konten yang bisa overflow | Tidak diubah |

Catatan ScrollView + fokus: baris di dalam kelima ScrollView baru semuanya `focusable=true` (dari style/menu yang sama), dan ScrollView standar menggeser anak fokus ke dalam pandangan (`requestRectangleOnScreen`) — termasuk baris terakhir, yang kini punya bantalan padding bawah. ScrollView sendiri tidak fokusable sehingga tidak mencuri fokus.

## Keterbatasan fase 6.1 (jujur)

1. Semua perubahan **compile-verified dari VM**; rasa fokus (apakah stroke 3dp + skala cukup mencolok dari jarak sofa) dan perilaku auto-scroll baris terakhir hanya bisa dikonfirmasi user di TV.
2. Dialog custom AppActions: background window diganti setelah `show()` — pola standar, tapi pewarnaan frame dialog AppCompat antar-build TV bisa berbeda; kalau di TV masih ada sisa frame terang di sekeliling panel gelap, itu kosmetik dan dilaporkan saja untuk fase berikut.
3. `mot_focus_white` memakai ring emas (bukan putih) saat fokus — deviasi sadar dari aturan stroke putih, alasannya di atas; berlaku di pill tanggal/chip terpilih dan tombol Putar/Jeda.

---

# BUILD REPORT — Fase 6.2 (rilis DIAGNOSTIK: buku harian lifecycle layanan aksesibilitas) — v0.6.2

Tanggal: 2026-11-01 · Status akhir: **BUILD SUCCESSFUL — APK 0.6.2 jadi, badging + sertifikat + quotes terverifikasi.**
Konteks: di TV user (TCL 32S51K, Android 14), mengaktifkan **"Mot TV — Tombol Home"** di Setelan Aksesibilitas **selalu mental balik ke OFF** tepat sesudah OK — tanpa pesan apa pun. Yang sudah diverifikasi SEBELUM fase ini: deklarasi manifest benar (`exported=true`, `BIND_ACCESSIBILITY_SERVICE`, config XML valid); kode layanan tidak punya kandidat crash (onServiceConnected trivial, semua handler try/catch); user sudah menyingkirkan app-stopped-state (buka app dulu) dan sudah coba restart TV; build TvSettings TCL ini **tidak menyediakan entri "Izinkan setelan terbatas" di mana pun** di App info. Pertanyaan yang belum terjawab dan fase ini dirancang untuk menjawabnya: **apakah sistem PERNAH me-bind layanan ini sama sekali?** Fase ini **rilis diagnostik murni — logika override TIDAK berubah sama sekali**; yang ditambah hanya visibilitas.

## Hasil akhir

| Item | Nilai |
|---|---|
| APK (deliverable) | `~/workspace/smart-tv-launcher/MotTvLauncher-0.6.2-debug.apk` |
| Ukuran | 9.582.371 byte (±9,6 MB — praktis sama dengan 0.6.1; tidak ada dependensi baru) |
| applicationId | `com.mottv.launcher` |
| versionName / versionCode | **0.6.2 / 11** (dari `aapt dump badging`) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 (badging) |
| Sertifikat penanda tangan | SHA-256 **dda6fa9827737d4b447a3caa470fbec0838d534679f4579a0195ae116fe096d4** — identik keystore permanen → **0.6.2 bisa diinstal menimpa 0.6.1 TANPA uninstall** |
| Sanity | `assets/quotes.json` di dalam APK md5 **1600905b7551b553a97dd09fc0020849**, identik pool sumber — tidak berubah |

Perintah build hijau: pola offline persis fase 1–6.1 (`assembleDebug --offline --no-daemon`, JDK tools, GRADLE_OPTS heap; BUILD SUCCESSFUL, exit 0).

## Yang diimplementasikan

**1. `ServiceDiary.kt` (baru)** — object penyimpan buku harian lifecycle, backed by SharedPreferences file `service_diary`. Setiap entri = timestamp wall-clock (millis) + counter kumulatif:
- `created_at/count` ← `HomeInterceptService.onCreate`
- `connected_at/count` ← `onServiceConnected` (dicap SEBELUM logika lain di callback itu — fakta "baris ini pernah jalan" adalah data diagnostik terpenting)
- `unbound_at/count` ← `onUnbind` (mengembalikan nilai super apa adanya)
- `destroyed_at/count` ← `onDestroy`
- `interrupt_count` ← `onInterrupt`
- `last_error` (class sederhana + message + timestamp) ← ditulis oleh **SETIAP catch block** di layanan sebelum exception ditelan: keempat situs terverifikasi via grep — `onKeyEvent`, `onAccessibilityEvent`, `onServiceConnected`, `launchHome` (masing-masing 1 panggilan `ServiceDiary.recordError`).
Semua penulisan diary dibungkus try/catch di dalam `ServiceDiary` sendiri — diary **tidak bisa melempar** balik ke kode layanan; `snapshot()` yang gagal pun jatuh ke Snapshot kosong.

**2. Wiring di `HomeInterceptService`** — hanya penambahan stamp + recordError di atas; urutan & isi logika override (filter KEYCODE_HOME, jaring pengaman window, debounce, grace window) **byte-for-byte sama secara perilaku** dengan 6.0/6.1.

**3. Kartu "Diagnostik Layanan" di `HomeOverrideActivity`** — LinearLayout bergaya kartu yang sama (`menu_item`) disisipkan di layout `activity_home_override` tepat di bawah kartu penjelasan, di dalam ScrollView yang sudah ada (jadi ikut aman dari audit scroll 6.1); di-refresh tiap `refreshStatus()` (onCreate + onResume — termasuk tepat sepulang user dari halaman Setelan Aksesibilitas). Isinya:
- "Terhubung terakhir: HH:mm:ss dd MMM" (format `SimpleDateFormat` locale id-ID) — atau "Belum pernah terhubung"
- "Jumlah terhubung: N · Terputus terakhir: <waktu>" (terputus = yang terakhir dari unbind/destroy)
- "Kesalahan terakhir: <class>: <message> @ <waktu>" — atau "Kesalahan terakhir: Tidak ada"
- **Satu baris interpretasi** (emas) yang dihitung dari data:
  - connections == 0 → *"Layanan belum pernah dihubungkan sistem — TV menolak mengaktifkan layanan ini (kemungkinan kunci 'setelan terbatas' Android untuk aplikasi sideload)."*
  - terputus terakhir ≤10 detik sesudah connect terakhir → *"Layanan sempat terhubung lalu dilepas sistem dengan cepat."*
  - selain itu → *"Layanan pernah terhubung normal."*

## Cara membaca hasilnya di TV (prosedur untuk user)

1. Instal 0.6.2 (timpa langsung), coba aktifkan **Mot TV — Tombol Home** seperti biasa (dan biarkan mental seperti biasanya).
2. Buka Pengaturan launcher → **Tombol Home Langsung ke Sini** → baca kartu **Diagnostik Layanan** paling bawah.
3. Laporkan tiga hal: jumlah terhubung, kesalahan terakhir (kalau ada), dan baris interpretasinya. Tiga skenario yang terbedakan bersih:
   - **0 connect + tidak ada error** → sistem tidak pernah me-bind: penolakan murni di sisi TV (kunci setelan terbatas tanpa UI, atau kebijakan TvSettings TCL) → jalan keluar berikutnya adalah keputusan user (mis. izinkan lewat jalur lain), bukan bug app.
   - **connect > 0 lalu terputus <10 dtk** → sistem me-bind lalu melepas: kandidatnya konflik kebijakan layanan (mis. layanan dianggap crash oleh framework di luar catch kita, atau TCL mematikan layanan pihak ketiga) → data timestamp-nya yang bicara.
   - **error tercatat** → bug nyata di pihak kami dengan nama class-nya; perbaiki berbasis bukti itu.

## Keterbatasan fase 6.2 (jujur)

1. Rilis ini **tidak memperbaiki** penolakan aktivasi — ia membuat penolakan itu *terlihat*. Klaim apa pun soal penyebab sebelum data diary di TV terbaca adalah tebakan; tiga skenario di atas disiapkan supaya laporan user berikutnya langsung menunjuk penyebabnya.
2. Semua perubahan compile-verified dari VM (build hijau, badging, sertifikat, md5 quotes, grep wiring) — perilaku diary di perangkat menunggu uji TV user, seperti fase-fase sebelumnya.
3. Diary bersifat kumulatif lintas versi (SharedPreferences tidak ter-reset oleh update timpa) — angka "jumlah terhubung" mencakup percobaan dari sesi-sesi sebelumnya sejak 0.6.2 terinstal; timestamp "terakhir" adalah penunjuk utama untuk percobaan terbaru.

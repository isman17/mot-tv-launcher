# Pool Quote Tokoh Bangsa — Smart TV Launcher

Pool kutipan Bahasa Indonesia dari tokoh bangsa untuk ditampilkan di home launcher
(rotasi 1 quote per hari di atas row aplikasi).

- **File data:** `quotes.json` — array of `{"figure", "quote", "source"}`
- **Total compiled:** **123 quotes**
- **Jumlah tokoh:** 28
- **Target akhir:** 300–500 quotes (pool ini adalah batch pertama yang terverifikasi)

## Jumlah per tokoh

| Tokoh | Jumlah |
|---|---|
| Soekarno | 16 |
| R.A. Kartini | 15 |
| Ki Hadjar Dewantara | 13 |
| Buya Hamka | 9 |
| Tan Malaka | 9 |
| Pramoedya Ananta Toer | 8 |
| Abdurrahman Wahid (Gus Dur) | 8 |
| Jenderal Soedirman | 7 |
| Soe Hok Gie | 7 |
| B.J. Habibie | 6 |
| Mohammad Hatta | 5 |
| Mohammad Yamin | 3 |
| Bung Tomo | 2 |
| Chairil Anwar | 2 |
| Sutan Sjahrir | 1 |
| Kapitan Pattimura | 1 |
| Cut Nyak Dien | 1 |
| Cipto Mangunkusumo | 1 |
| Abdul Muis | 1 |
| Gubernur Suryo | 1 |
| Teuku Nyak Arif | 1 |
| I Gusti Ngurah Rai | 1 |
| Supriyadi | 1 |
| Silas Papare | 1 |
| Agus Salim | 1 |
| K.H. Ahmad Dahlan | 1 |
| K.H. Hasyim Asy'ari | 1 |
| **Total** | **123** |

## Standar verifikasi

Hanya quote yang sumbernya bisa dicek yang masuk pool ini:

1. **Wikiquote Bahasa Indonesia** (id.wikiquote.org) — diutamakan entri yang di halaman
   tersebut mencantumkan sumber buku/pidato/surat secara eksplisit.
2. **Buku terbit** — pidato (BPUPKI 1 Juni 1945, pidato HUT Proklamasi, pidato Hari
   Pahlawan 1961), surat-surat Kartini, *Menuju Republik Indonesia* & *Aksi Massa*
   (Tan Malaka), *Madilog*, *Catatan Seorang Demonstran* (Soe Hok Gie), novel Pramoedya
   (*Bumi Manusia*, *Anak Semua Bangsa*), *100 Tokoh yang Mengubah Indonesia* (Narasi,
   2005), biografi Soedirman terbitan Departemen Kebudayaan dan Pariwisata RI dan
   Pusat Sejarah TNI, *Memoir* Mohammad Hatta.
3. **Sumber resmi .go.id / institusi** — daftar "Pesan Perjuangan Pahlawan Nasional"
   Kemensos RI (pedoman resmi Peringatan Hari Pahlawan), KPU, Pemprov Sumbar.
4. **Media/universitas bereputasi** yang mengutip sumber primer di atas.

Yang **sengaja TIDAK dipakai**: listicle quote viral tanpa sumber (brilio, bola.net,
dianisa, kapanlagi listicle, popbela, blog kata-mutiara, dll.) kecuali bila isinya bisa
dilacak balik ke sumber primer di atas.

Catatan kejujuran sumber:

- Daftar "Pesan Perjuangan" Kemensos adalah atribusi tradisional resmi pemerintah —
  dipakai untuk tokoh era pra-kemerdekaan yang memang tidak meninggalkan tulisan
  (Pattimura, Cut Nyak Dien, dll.). Waspada: beredar juga daftar "pesan pahlawan"
  versi baru (2025) di situs konten dengan quote yang terasa generik/karangan —
  versi itu **tidak** dipakai di sini.
- 6 quote B.J. Habibie berasal dari memoir *Habibie & Ainun* / filmnya (Wikiquote
  "Habibie dan Ainun") — tier sumber paling lemah di pool ini.
- 1 quote Jenderal Soedirman dipertahankan dalam **ejaan lama** sesuai sumbernya
  (Senakatha, Pusat Sejarah TNI).
- Normalisasi ringan hanya pada kapitalisasi awal kalimat dan tanda baca akhir;
  kata-kata tidak diubah. Varian near-duplicate dibuang — dipilih varian dengan
  sumber terkuat (mis. kalimat Schiller yang sering disalahatribusikan ke Sjahrir
  **tidak** dimasukkan).
- 12 dari 123 quote panjangnya >160 karakter (termasuk quote ikonik "dua sayap"
  Soekarno dari *Sarinah* dan "jangan takut jatuh" Hamka) — sisanya ≤160 karakter,
  ramah tampilan TV.

## Tokoh yang perlu ekspansi

**Belum ada quote terverifikasi (0) — jangan dipaksakan dari listicle:**

- **Pangeran Diponegoro** — tidak ditemukan verbatim yang bisa dilacak ke sumber
  primer (Babad Diponegoro berbahasa Jawa). Quote "pesan Diponegoro" yang beredar
  hanya muncul di daftar generik tanpa sumber.
- **Tuanku Imam Bonjol** — sama; tidak ada verbatim tertulis yang terverifikasi.
- **W.R. Supratman** — warisannya lirik lagu, bukan quote lisan/tulisan terverifikasi.
- **Ismail Marzuki** — sama; kandidat ekspansi hanya dari lirik lagunya bila nanti
  diputuskan lirik boleh masuk pool.

**Masih tipis (1–6) dan prioritas ditambah:**

- **Sutan Sjahrir (1)** — gali *Perjuangan Kita* (1945) dan *Renungan dan Perjuangan*
  (Djambatan/Dian Rakyat, 1990) untuk verbatim asli.
- **Mohammad Hatta (5)** — gali *Memoir* (1979) dan esai-esainya; halaman Wikiquote
  Hatta sangat tipis.
- **Mohammad Yamin (3)**, **Cut Nyak Dien (1)**, **Agus Salim (1)** — gali arsip
  Kemdikbud/Museum Sumpah Pemuda dan *Djedjak Langkah Hadji A. Salim* (1954).
- **B.J. Habibie (6)** — ganti/tambah dari pidato dan bukunya
  (*Detik-Detik yang Menentukan*, 2006) agar keluar dari tier memoir/film.
- Semua tokoh ber-quote tunggal dari daftar Kemensos (Pattimura, Cipto, Abdul Muis,
  Gubernur Suryo, Teuku Nyak Arif, I Gusti Ngurah Rai, Supriyadi, Silas Papare).

**Rute menuju 300–500:** korpus pidato lengkap Soekarno (*Di Bawah Bendera Revolusi*,
pidato HUT Proklamasi 1945–1966), surat lengkap Kartini (*Habis Gelap Terbitlah Terang*
edisi lengkap), *Karya Ki Hadjar Dewantara*, buku-buku Hamka (*Tasawuf Modern*,
*Lembaga Hidup*, *Falsafah Hidup*), tetralogi Pramoedya lengkap, karya lengkap
Tan Malaka, dan tulisan Hatta/Sjahrir — semuanya bisa dipanen per kalimat utuh
dengan sumber halaman yang jelas.

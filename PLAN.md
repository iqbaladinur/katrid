# KARTRID — frontend emulator (GB / GBC / GBA) untuk Android
Scope: frontend **bukan** emulator, **bukan** home launcher. Cuma rak kartrid + launcher ke RetroArch.
Status: **PLAN v2** — belum ada kode. Mockup portrait v1 (`design/shots/*.png`) + landscape v2 (`design/shots/l*.png`).
Target device: **KONKR Pocket Advance**.

---

## 1. Verdict: nggak ribet (kalau scope tetap dijaga)

Yang bikin Daijisho "besar" bukan tampilannya — tapi 20+ sistem, scraper, themes, Android TV, frontend API.
Scope lo = **3 sistem + 1 emulator target**. Bagian yang benar-benar teknis cuma dua:

| Bagian | Kesulitan | Catatan |
|---|---|---|
| Launch ke RetroArch | **Gampang** | API-nya kecil, stabil, ke-verifikasi dari source RetroArch (lihat §7) |
| Scan ROM + meta | Gampang | `.gb .gbc .gba` + file stat, no DB engine needed |
| Rak kartrid (UI) | Sedang | Compose carousel + komposit label; ini bagian "seni", bukan teknik |
| Label gambar (art) | **Paling nyusahin** | Matching nama ROM → nama No-Intro; source udah ketemu (§8) |
| Izin file Android 11+ | Sedang, sekali kerja | 2 jalur (API<30 vs ≥30), sekali setup lalu selesai |

Estimasi realistis: **MVP enak dipakai ≈ 4–6 hari kerja efektif** (lo sendiri, paruh waktu). Bukan proyek bulanan.

---

## 2. Yang SENGAJA di luar scope (biar nggak melar)

- Jadi home launcher (`CATEGORY_HOME` intent-filter) — nanti aja kalau mau.
- Emulator built-in (libretro core di dalam APK) — RetroArch yang ngerjain.
- Scraper meta online (sinopsis, genre, rating) — cuma gambar.
- Save-state manager, netplay, achievement, cheat DB.
- Multi-emulator per sistem (AetherSX2/PPSSPP dsb) — cuma RetroArch dulu.
- Play Store. **Sideload**, ini penting: lihat §9 soal `MANAGE_EXTERNAL_STORAGE`.

---

## 3. Stack

| Layer | Pilihan | Alasan |
|---|---|---|
| Bahasa | **Kotlin** | (lo minta) — bukan Java |
| UI | **Jetpack Compose + Material3** | Carousel (LazyRow + `snapFlingBehavior`), animasi lift kartrid gampang |
| Gambar | **Coil 3** | load PNG label + cache disk |
| Prefs | **DataStore (Proto/Preferences)** | folder ROM, core per sistem, toggle |
| Library | **JSON via kotlinx.serialization** | 400-800 game → gak butuh Room; in-memory index cukup |
| Min SDK | 26 (Android 8) | target 35 |
| Build | JDK 17 (Temurin tarball) + `cmdline-tools` + Gradle wrapper | **tanpa apt** — sesuai aturan host; semua di `~/android-dev` |
| Deploy | `adb install -r` (ZenFone udah ada adb) | iterasi cepat |

Layout mockup ini dipakai konsisten sama design system lo: **riso-volt dark** (`#16121F` bg / `#A98BFF` violet / `#D4F24E` lime), DNA flat, hard offset shadow 0-blur, border ink. Font: Archivo Black (judul), Inter (body), JetBrains Mono (meta/label).

---

## 3b. Revisi v2 — landscape KPA, main view simple, animasi "masuk slot"

Target device = **KONKR Pocket Advance**: layar **960×640 landscape (3:2)**, Android 12, kontrol **D-pad + A/B/X/Y, TANPA analog stick**.
Konsekuensinya:

| Perubahan | Detail |
|---|---|
| Orientasi | Semua layar **landscape**, portrait dibuang. Game juga landscape (GBA 3:2 pas banget). |
| Navigasi | **D-pad-first**: ◀▶ pindah kartrid · **A** masukkan · **X** favorit · **Y** cari · **B** pengaturan. Sentuh tetap ada (tap = A). Gak ada analog → jangan bikin kontrol yang butuh stick. |
| Main view | **Disederhanain drastis** — dari 7 zona jadi **4**: tab sistem (mono, aktif = underline lime) · rak · judul + meta 1 baris · hint tombol. Brand header, filter chip, counter, dan strip 3 tombol **dibuang**. |
| Pengaturan | Boleh detail → pola **master–detail**: kiri daftar grup (250 dp), kanan isi grup. Enak di landscape, gak perlu scroll panjang. |
| Izin | KPA = Android 12 → jalur `MANAGE_EXTERNAL_STORAGE` (§9). Sideload, jadi aman. |

### Storyboard animasi + SFX "klik kartrid"
1. **Idle** — kartrid fokus naik 8, outline lime, slot menganga di papan, tetangga nyembul lebih gelap.
2. **Tekan A** — kartrid turun ke slot (`translateY +58`, `scale .96`), slot lip naik, hint bar ganti jadi "MENUJU SLOT…".
3. **Nyentuh mulut slot** — kartrid **ke-clip** (`inset(0 0 46% 0)` — sisa bagian atas kelihatan), **flash + ring lime** di mulut slot, **SFX klik** + haptic ~18 ms.
4. **Terpasang** — kartrid hilang, overlay "MEMUAT <judul>" + progress bar → launch intent.
5. **Keluar game** — balik ke rak, animasi mundur + `eject.ogg`.

**SFX buatan sendiri** (`scripts/gen-sfx.py`, numpy + ffmpeg — **bukan** sample Nintendo, jadi bebas lisensi):

| File | Durasi | Isi | Ukur |
|---|---|---|---|
| `assets/sfx/insert.ogg` | 167 ms | klik plastik keras + thunk 92 Hz + latch kedua @31 ms | peak 0.69 · RMS 0.105 · attack 0.2 ms · centroid 5.1 kHz |
| `assets/sfx/eject.ogg` | 167 ms | latch lepas (lembut) lalu pop di 56 ms | peak 0.67 · RMS 0.129 |
| `assets/sfx/tick.ogg` | 42 ms | klik kecil buat gerak D-pad | peak 0.53 · centroid 1.3 kHz |
| `assets/sfx/demo.mp3` | 1.27 s | insert – jeda 0.9 s – eject (buat review) | — |

Implementasi Compose: `SoundPool` (bukan MediaPlayer — latency rendah, penting buat klik), animasi `Animatable` + `Modifier.drawWithContent { clipRect(...) }`, haptic `Vibrator` 18 ms (kalau KPA punya motor getar — belum gw konfirmasi; fallback diam).

### Layout landscape (angka dari mockup v2, basis 960×640)
- **Top bar 38**: tab GB/GBC/GBA mono 13 letterspacing 1.4 (aktif lime + underline 3), gear 26×26 di kanan.
- **Rak**: plank 14 tinggi, border 2 + shadow 6, dari x132 ke x828. **Slot**: 196×16 di tengah plank (hitam + lip violet). Kartrid fokus **288×206**, tetangga **196×140** (gelap `saturate .6 brightness .62`), gap 34 → fokus pas di tengah (x 336–624).
- **Judul** di bawah plank: Archivo 27 + meta mono 11.5 (region · ukuran · jumlah main).
- **Hint** 11 mono: `◀▶ pindah · A masukkan · X favorit · Y cari · B pengaturan` (tombol di kotak border).
- **Pengaturan**: nav 250 lebar (aktif = lime + bar kiri 4), baris 52, switch kotak 46×26, radio chip mono 10.5.

---

## 4. Peta layar

```
S5a Setup 1/3 (izin file) → S5b Setup 2/3 (folder ROM) → S5c Setup 3/3 (emulator+core)
                                                                   ↓
S4 Pengaturan ← gear ─┐
S3 Cari & Daftar ← ⌕ ─┤→  S1 RAK (HOME)  ──tap kartrid──→  MAINKAN ──intent──→ RetroArch
                      │        │                                                    │
                      │        └──long-press──→ S2 Detail game (bottom sheet)        │
                      └───────────────←── keluar game (QUITFOCUS) ──────────────────┘
```
- **S1 RAK = home.** Gak ada bottom-nav. Semua aksi minoritas (cari, setting) jadi ikon di header.
- **S2** bottom sheet, bukan halaman penuh → balik ke rak tanpa kehilangan posisi carousel.
- **S3** list mode: buat yang punya 200+ ROM, geser satu-satu di rak itu nyiksa.
- Layar dalam game = RetroArch, bukan punya kita.

---

## 5. Menu — daftar lengkap

### S1 RAK (home)
| Elemen | Fungsi |
|---|---|
| Tab **GB / GBC / GBA** (+ jumlah) | ganti rak per sistem |
| Filter **SEMUA / ★ FAVORIT / TERAKHIR** | saring rak |
| Ikon ⌕ | → S3 cari |
| Ikon ☰ | → S4 pengaturan |
| Geser kartrid + snap | pilih game; yang kepilih naik + outline lime + bayangan keras |
| Tap kartrid terpilih / tombol **MAINKAN** | launch ke RetroArch |
| Long-press kartrid | → S2 detail |
| Counter `2 / 214` | posisi + hint geser |
| Strip bawah | judul, region, ukuran, jumlah main, ★, **GANTI GAMBAR**, ⋯, **MAINKAN** |
| Empty state | rak kosong → "Belum ada ROM" + tombol "Pilih folder ROM" |
| Indikator scan | pita tipis "memindai… 128/438" + tombol stop |

### S2 Detail game (bottom sheet)
Meta: Sistem · Core · Ukuran · Terakhir main · Total main · Durasi · Path (mono, tap = salin)
Aksi: **Favorit** · **Ganti gambar** (pilih PNG dari galeri, buat override) · **Sembunyikan dari rak** · **Salin path** · **MAINKAN** (primary).

### S3 Cari & Daftar
Search field · chip sistem multi-pilih (GB/GBC/GBA/★/TERSEMBUNYI) · baris hasil dengan mini-kartrid · rail A–Z · tombol sort (A→Z, terakhir main, ukuran) · tap = S2, long-press = MAINKAN langsung.

### S4 Pengaturan
| Grup | Item |
|---|---|
| **Perpustakaan** | Folder Game Boy / Game Boy Color / Game Boy Advance (masing-masing path + jumlah) · Pindai ulang · Game disembunyikan (daftar) · Pindai otomatis saat dibuka (switch) |
| **Emulator** | Aplikasi target (varian RetroArch yang kedeteksi) · Core per sistem (radio: GB/GBC → gambatte/sameboy/gearboy, GBA → mGBA/gpSP/VBA-M) · Keluar saat keluar game (QUITFOCUS) · Paksa 60 Hz (REFRESH) · Argumen tambahan (dialog teks) |
| **Tampilan** | Gaya rak (Volt/Abu/Kayu) · Ukuran kartrid S/M/L · Orientasi (portrait / ikut perangkat) · Tampilkan label region (switch) · Animasi (switch) |
| **Gambar** | Sumber (unduh saat dibutuhkan / bundle offline) · Ukuran cache + Bersihkan · Unduh otomatis (switch) |
| **Data & Tentang** | Ekspor/impor library (JSON) · Reset statistik main · Versi · Kredit · Buka setting RetroArch |

### S5 Setup (sekali, 3 langkah)
1. **Izin akses file** — penjelasan + tombol ke setting izin (+ "lewati")
2. **Folder ROM per sistem** — pilih folder, langsung dihitung "128 file .gb"
3. **Emulator** — deteksi RetroArch terpasang + core terpasang (via broadcast, §7) → "Mulai memindai"

---

## 6. Layout (angka dari mockup, satuan dp; basis 393×786)

**S1 RAK**
- status 26 · header 52 (brand Archivo 15.5 di kiri, 2 ikon 34×34 border 1.5 + shadow 3 di kanan)
- tab 34 tinggi, gap 8, aktif = violet + shadow 3
- filter row: mono 10.5 uppercase, aktif = underline lime 2.5
- **rak**: tinggi area 424. Kartrid duduk di papan (bar 16 tinggi, border-top ink, shadow bawah 5). Garis slot vertikal di backplate tiap 120.
  Kartrid GBA **158×112**, GB/GBC **142×158** (diputar: papan tidur, kartrid berdiri), gap 26, `padding` di-offset `translateX(-66)` biar yang kepilih **center** (x 120–278) dan tetangganya nyembul di tepi (kiri −64…94, kanan 304…462) → kerasa rak beneran, bukan grid.
  Terpilih: naik 20, outline lime 3 (offset 2), shadow 7. Lainnya: `saturate .72 brightness .78` + shadow 4.
- strip bawah: panel surface border 1.5 + shadow 5, padding 10; baris 1 = bintang 34 + judul Archivo 15 (ellipsis 1 baris) + meta mono 9.5; baris 2 = 2 ghost button 40 tinggi + **MAINKAN** lime 40 tinggi (Archivo 13) rata kanan.

**S2 sheet**: tinggi ± 560 dari bawah, radius atas 14, shadow atas 7 · grip 44×4 · grid meta 2 kolom · baris aksi 44 tinggi (separator hairline) · primary 50.

**S3**: header = back 34 + search field 38 (flex) + sort 38 · chip 28 · baris list 64 (thumb mini kartrid 46×34 / 40×44) · rail A–Z mono 8.5 di kanan.

**S4**: grup judul mono 9.5 letterspacing 1.2 · baris 52 min (label 13 + sub mono 9.5) · switch 42×24 kotak (bukan pill) · radio chip mono 9.5.

**S5**: panel ikon 88×88 violet + shadow 6 · h2 Archivo 21 · body Inter 13.5/1.5 · note border-left lime 3 · primary 50 · dots 22×4.

Aturan turunan: semua sudut **kotak/hard** (radius ≤ 14), gak ada blur/gradient/glass; hijau lime cuma buat "state aktif + primary CTA", violet buat "identitas + aksi sekunder".

---

## 7. Kontrak launch ke RetroArch — VERIFIED dari source (bukan dari ingatan)

RetroArch Android punya activity yang bisa dipanggil app lain:
`<activity android:name="com.retroarch.browser.retroactivity.RetroActivityFuture" android:exported="true" ...>`

```kotlin
val i = Intent().apply {
  setClassName(pkgRetroArch, "com.retroarch.browser.retroactivity.RetroActivityFuture")
  putExtra("ROM", romPath)          // path filesystem asli, bukan content://
  putExtra("LIBRETRO", corePath)    // /data/data/<pkgRetroArch>/cores/mgba_libretro_android.so
  putExtra("CONFIGFILE", cfgPath)   // WAJIB — ini yang bikin RetroArch mode "viaLauncher"
  putExtra("QUITFOCUS", true)       // hasExtra() → kalau true, RetroArch exit saat kehilangan fokus
  putExtra("REFRESH", "60")         // preferredRefreshRate (opsional)
  addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
}
startActivity(i)
```
Sumber: `pkg/android/phoenix/AndroidManifest.xml`, `RetroActivityFuture.java`, `RetroActivityCommon.java:348`
(`boolean viaLauncher = getIntent() != null && getIntent().hasExtra("CONFIGFILE");`), contoh resmi path-nya di docs Pegasus.

**Nama paket RetroArch bervariasi** — jangan hardcode, cek berurutan:
`com.retroarch` · `com.retroarch.aarch64` (Play Store 64-bit) · `com.retroarch.ra32` (nightly 32-bit) · `com.retroarch.quest`.
`corePath` ikut paket: `/data/data/<pkg>/cores/<core>.so`. Path itu **nggak perlu bisa dibaca** app kita — RetroArch yang resolve sendiri.

**Deteksi core terpasang tanpa root** (bisa banget, ini jalur resmi):
kirim broadcast `com.retroarch.QUERY_INSTALLED_CORES` → RetroArch jawab `com.retroarch.INSTALLED_CORES_RESULT` + extra `CORES` (`String[]` nama file core). Receiver-nya manifest-registered, jadi jalan walau RetroArch belum dibuka. Sumber: `InstalledCoresReceiver.java`.

**Nama core** (dari buildbot nightly `android/latest/arm64-v8a/`):
GB/GBC → `gambatte_libretro_android.so` · `sameboy_libretro_android.so` · `gearboy_libretro_android.so`
GBA → `mgba_libretro_android.so` · `gpsp_libretro_android.so` · `vbam_libretro_android.so`

Detail UX: **QUITFOCUS sebaiknya di-OFF saat pertama kali** (user mesti setup RetroArch dulu: izin, download core, mapping). Baru nyalain setelah RetroArch terkonfigurasi — kalau ON dari awal, tiap ada dialog izin RetroArch langsung `System.exit(0)`.

---

## 8. Gambar label kartrid — pipeline yang udah kebukti

Sumber: **`https://thumbnails.libretro.com/<System>/Named_Boxarts/<nama No-Intro>.png`** (CDN resmi libretro-thumbnails, index-nya kelihatan + langsung HTTP 200). Gw udah pakai ini buat mockup: 15 cover ke-download (GB/GBC/GBA), rata-rata ~430 KB/PNG.

- Nama file = **konvensi No-Intro**: `Pokemon - Emerald Version (USA, Europe).png`, `Tetris (World) (Rev 1).png`.
- Ukuran repo kalau di-clone utuh: GB **695 MB**, GBC **553 MB**, GBA **1.8 GB** → **jangan** clone. Ambil per-judul on-demand + cache di app.
- **Cartridge image gak ada** di libretro-thumbnails (folder-nya cuma `Named_Boxarts / Named_Logos / Named_Snaps / Named_Titles`). Jadi:
  - **Rencana A (dipakai di mockup):** template shell kartrid digambar sendiri per sistem (GB abu + notch · GBC plastik bening kebiruan + sudut bulat · GBA indigo + notch), boxart ditempel dalam area label + band nama sistem. Konsisten, offline-friendly, nggak perlu akun.
  - **Rencana B:** API ScreenScraper punya media `cartridge` asli (foto kartrid per game) — butuh akun dev + quota request + rate limit. Simpan buat fase 2.
- Matching nama ROM → nama thumbnail = **kerjaan paling nyebelin**. Normalisasi: buang tag GoodTools (`[!]`, `[a1]`, `(Rev 1)`, `(U)`), samakan region, fallback coba tanpa suffix region. Tetap ada yang zonk (romhack/terjemahan) → sediakan **override manual ("Ganti gambar")** dan jangan auto-retry tiap scan (cache hasil "gagal" juga).
- Cache: 1 PNG ±430 KB; 400 game ≈ 170 MB, kecilkan ke 256×256 → ±35 MB.

---

## 9. Izin & storage

| Android | Cara ambil akses | Catatan |
|---|---|---|
| ≤ 10 (API 29-) | `READ_EXTERNAL_STORAGE` runtime | ZenFone Max Pro M1 ada di sini — jalur gampang |
| 11+ (API 30+) | `MANAGE_EXTERNAL_STORAGE` via `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION` | RetroArch sendiri pakai ini; wajib kalau kita mau kasih path filesystem ke RetroArch |

- `content://` URI (SAF) **nggak bisa** dipakai buat launch RetroArch — butuh path asli. Jadi SAF cuma buat *pilih folder*, bukan buat jalanin game.
- `MANAGE_EXTERNAL_STORAGE` **ditolak Play Store** kecuali app kategori file-manager/antivirus. Konsekuensinya: **sideload**. (Kalau suatu saat mau ke Play Store: pakai `READ_MEDIA_*` + pindahkan ROM ke folder app sendiri, atau tetap butuh RetroArch yang punya izinnya.)
- Manifest perlu `<queries><package android:name="com.retroarch"/>…</queries>` (semua varian) supaya bisa deteksi RetroArch terpasang & kirim broadcast-nya.
- Bonus Android ≤10: playlist RetroArch (`.../Android/data/com.retroarch/files/playlists/*.lpl`) bisa dibaca langsung — isinya path ROM + core + label. Di Android 11+ folder itu **diblokir** buat app lain, jadi jangan dijadikan andalan.

---

## 10. Data model (versi ringkas, cukup buat MVP)

```kotlin
@Serializable data class Game(
  val id: String,            // hash(system + path)
  val system: System,        // GB/GBC/GBA
  val path: String,
  val title: String,         // dari nama file, bisa di-rename user
  val region: String?, val sizeBytes: Long, val modifiedAt: Long,
  val fav: Boolean = false, val hidden: Boolean = false,
  val artPath: String? = null, val artState: ArtState = ArtState.NEVER, // NEVER/OK/FAILED
  val plays: Int = 0, val totalMs: Long = 0, val lastPlayedAt: Long? = null)
data class Settings(folders: Map<System,List<String>>, cores: Map<System,String>,
  retroPkg: String?, quitFocus: Boolean, force60: Boolean, cartSize: Int,
  shelfStyle: String, autoScan: Boolean, autoArt: Boolean)
```
Scan: jalan di `Dispatchers.IO`, `Flow<ScanProgress>` (ditemukan/total/path) biar UI bisa nampilin progress + bisa di-stop. Refresh inkremental pakai `modifiedAt` (jangan rescan ribet tiap buka).

---

## 11. Estimasi kerja

| # | Item | Estimasi |
|---|---|---|
| 0 | Setup toolchain (JDK + cmdline-tools + project kosong + `adb install` jalan) | ½ hari |
| 1 | Scan ROM + library JSON + 3 folder | 1 hari |
| 2 | Rak kartrid (Compose, snap, lift, peek) + tab + filter | 1–1½ hari |
| 3 | Shell kartrid per sistem + komposit label + cache art + normalisasi nama | 1–1½ hari |
| 4 | Launch intent + deteksi RetroArch/core + QUITFOCUS + kembali ke rak | ½ hari |
| 5 | S2/S3/S4/S5 + empty/loading/error state + polish | 1–1½ hari |
| 6 | Test di HP, perbaikan sentuhan, ikon app | ½ hari |
| | **Total MVP** | **≈ 5–7 hari** |

---

## 12. Pitfall yang bakal ketemu (siapin dari awal)

1. **`QUITFOCUS` + dialog izin = app keluar sendiri.** Nyalain belakangan, jangan default.
2. **Path `.so` core beda per varian paket** — derive dari paket yang kedeteksi, jangan hardcode `com.retroarch`.
3. **Satu activity alias untuk semua sistem**: launchMode RetroArch `singleInstance`; kalau launch game kedua sementara RetroArch hidup, dia restart task sendiri (source: `onNewIntent`). Jangan kaget, jangan nambah flag aneh.
4. **Nama ROM realita**: `Pokemon Emerald.gba` (tanpa region) vs thumbnail `...(USA, Europe)`. Wajib normalizer + fallback + override manual.
5. **ROM di SD card eksternal** = path `/storage/XXXX-XXXX/...` — pastikan picker dukung, dan path tetap valid setelah reboot (jangan simpan URI persist, simpan path + validasi saat scan).
6. **Scan 1.8 GB folder** bisa makan detik → progress + cancel, jangan freeze UI.
7. **Gambar 430 KB × ratusan** → decode `inSampleSize` + cache 256px, kalau nggak, OOM di HP 3-4 GB.
8. **Play Store ≠ target**, jadi jangan buang waktu bikin kebijakan privasi; fokus sideload.

---

## 13. Keputusan

**Udah dijawab (14 Okt):** landscape di KPA · D-pad-first · main view simple & cantik · setting boleh detail · animasi kartrid masuk slot + SFX klik.

**Masih perlu lo putusin:**
1. **Gambar label**: template shell sendiri (Rencana A — ilang nunggu, offline, konsisten) atau foto kartrid asli dari ScreenScraper (Rencana B — butuh akun + quota)?
2. **SFX**: versi yang gw bikin udah pas? atau mau varian lain (lebih "plastik"/lebih "berat-bass") — gampang diubah, tinggal ganti parameter di `gen-sfx.py`.
3. **Art default**: unduh saat dibutuhkan (APK kecil, butuh wifi sekali) atau bundle sebagian di APK (offline total, APK gede)?
4. **KPA punya motor getar?** Kalau iya, haptic 18 ms bakal ikut nempel di klik. Lo bisa cek: Settings → Sound & vibration.

Lanjutan yang bisa gw kerjain sekarang: **(a)** setup toolchain (JDK + cmdline-tools + project Compose) sampai APK "hello shelf" kepasang di KPA, atau **(b)** mockup v3 buat layar yang belum digambar (cari, detail game, setup wizard) dalam landscape.

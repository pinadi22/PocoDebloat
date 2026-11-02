# Poco Debloat

Aplikasi Android untuk menghapus (atau menonaktifkan) bloatware di POCO X3 Pro (MIUI 13 / Android 12) langsung dari HP, tanpa kabel dan tanpa PC. Aplikasi memakai [Shizuku](https://github.com/RikkaApps/Shizuku) untuk menjalankan perintah dengan hak akses `adb shell`.

## Peringatan

Dipakai dengan risiko sendiri. Daftar preset (`presets.json`) disusun dari pengetahuan umum dan diuji pada satu perangkat (POCO X3 Pro, MIUI Global 13.0.5), jadi belum tentu cocok untuk perangkat atau versi MIUI lain. Label **Aman** bukan jaminan: MIUI kadang menitipkan komponen penting di paket yang namanya terdengar sepele (contoh nyata: `com.android.htmlviewer` memuat provider yang dibutuhkan layar Recents).

Saran: hapus sedikit-sedikit, uji setelah tiap batch, dan simpan daftar yang dihapus. Kalau ada yang bermasalah, buka tab **Dipulihkan**. Kalau launcher macet dan layar tidak bisa dipakai, buka aplikasi lewat Setelan > Aplikasi > Kelola aplikasi, atau pulihkan lewat ADB/Shizuku:

```
cmd package install-existing --user 0 <nama.paket>
```

## Cara membangun

1. Buka folder `PocoDebloat` di Android Studio (versi terbaru, JDK 17).
2. Tunggu Gradle sync selesai. Kalau diminta, izinkan Android Studio mengunduh Gradle 8.9.
3. Pilih **Build > Build APK(s)**, atau jalankan langsung ke HP lewat tombol Run.
4. Pasang APK di HP.

## Cara memakai

1. Pasang Shizuku di HP.
2. Aktifkan **Developer options > Wireless debugging**, lalu buka Shizuku dan pilih **Start via Wireless debugging** (ikuti panduan pairing di aplikasinya).
3. Buka Poco Debloat dan tekan **Minta izin**.
4. Di tab **Terpasang**, centang aplikasi yang ingin dibersihkan, lalu tekan **Hapus** atau **Nonaktifkan**.
5. Untuk mengembalikan, buka tab **Dipulihkan**, centang aplikasinya, lalu tekan **Pulihkan**.

Setelah HP di-restart, Shizuku perlu dijalankan lagi (Wireless debugging butuh Wi-Fi).

## Cara kerjanya

| Aksi | Perintah |
|---|---|
| Hapus | `pm uninstall -k --user 0 <paket>` |
| Nonaktifkan | `pm disable-user --user 0 <paket>` |
| Pulihkan (dihapus) | `cmd package install-existing --user 0 <paket>` |
| Pulihkan (nonaktif) | `pm enable --user 0 <paket>` |

Penghapusan hanya berlaku untuk user 0. File aplikasinya tetap ada di partisi sistem, jadi bisa dipulihkan, dan reset pabrik mengembalikan semuanya.

## Pengaman

- Setiap paket berlabel **Aman**, **Hati-hati**, **Jangan dihapus**, atau **Tidak dikenal** (daftar ada di `app/src/main/assets/presets.json`, silakan ditambah).
- Paket berlabel **Jangan dihapus** tidak bisa dicentang di tab Terpasang.
- Ada dialog konfirmasi dan mode **Simulasi** yang hanya menampilkan perintah tanpa menjalankannya.
- Nama paket divalidasi sebelum dimasukkan ke perintah shell.
- Nonaktifkan adalah pilihan yang lebih ringan daripada Hapus. Coba itu dulu untuk aplikasi berlabel Hati-hati.

## Kalau ada yang bermasalah

- **Perintah ditolak di MIUI:** aktifkan **USB debugging (Security settings)** di Developer options.
- **Sistem aneh setelah menghapus sesuatu:** buka tab Dipulihkan dan pulihkan paketnya. Kalau aplikasi tidak bisa dibuka lagi, jalankan dari PC: `adb shell cmd package install-existing --user 0 <paket>`.
- Aplikasi ini belum dicoba dikompilasi di perangkat nyata. Kalau ada error build, kirim pesan errornya.

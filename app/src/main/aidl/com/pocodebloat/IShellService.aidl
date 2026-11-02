package com.pocodebloat;

interface IShellService {
    // Kode transaksi 16777114 dicadangkan Shizuku untuk destroy()
    void destroy() = 16777114;

    // Mengembalikan "<exit code>\n<output gabungan stdout+stderr>"
    String exec(String cmd) = 1;
}

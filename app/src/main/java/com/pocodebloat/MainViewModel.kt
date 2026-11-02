package com.pocodebloat

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    companion object {
        private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
        private const val REQUEST_CODE = 1001
        private const val PREFS = "debloat"
        private const val KEY_HISTORY = "history"
    }

    // ---- state yang dibaca UI ----
    var status by mutableStateOf(ShizukuStatus.NOT_RUNNING); private set
    var hint by mutableStateOf<String?>(null); private set
    var items by mutableStateOf<List<AppItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var running by mutableStateOf(false); private set
    var selected by mutableStateOf<Set<String>>(emptySet()); private set

    var tab by mutableIntStateOf(0); private set          // 0 = terpasang, 1 = dipulihkan
    var query by mutableStateOf("")
    var levelFilter by mutableStateOf<Level?>(null)
    var method by mutableStateOf(Method.UNINSTALL)
    var dryRun by mutableStateOf(false)
    var showConfirm by mutableStateOf(false)
    var logText by mutableStateOf<String?>(null)

    // ---- internal ----
    private var shell: IShellService? = null
    private var binding = false
    private val repo = PackageRepo(app)
    private val presets: Map<String, Preset> = Presets.load(app)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var history: Set<String> = prefs.getStringSet(KEY_HISTORY, emptySet()) ?: emptySet()

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(app.packageName, ShellService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            if (binder != null && binder.pingBinder()) {
                shell = IShellService.Stub.asInterface(binder)
                status = ShizukuStatus.READY
                refresh()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shell = null
            binding = false
            status = ShizukuStatus.CONNECTING
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { checkStatus() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        shell = null
        binding = false
        status = ShizukuStatus.NOT_RUNNING
    }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, _ -> checkStatus() }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        checkStatus()
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        if (shell != null) {
            runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
        }
    }

    // ---- Shizuku ----

    private fun isShizukuInstalled(): Boolean = try {
        app.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun checkStatus() {
        if (!Shizuku.pingBinder()) {
            status = if (isShizukuInstalled()) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            status = ShizukuStatus.NO_PERMISSION
            return
        }
        hint = null
        if (shell == null && !binding) {
            binding = true
            status = ShizukuStatus.CONNECTING
            Shizuku.bindUserService(userServiceArgs, connection)
        }
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) return
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            hint = "Izin pernah ditolak permanen. Buka aplikasi Shizuku, masuk ke daftar " +
                "aplikasi yang diizinkan, lalu beri izin untuk Poco Debloat."
        } else {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun openShizuku() {
        val intent = app.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        }
    }

    // ---- daftar paket ----

    fun refresh() {
        val sh = shell ?: return
        viewModelScope.launch {
            loading = true
            items = withContext(Dispatchers.IO) { repo.load(sh, presets, history) }
            selected = emptySet()
            loading = false
        }
    }

    val visible: List<AppItem>
        get() = items.filter {
            val tabOk = if (tab == 0) it.state == AppState.INSTALLED else it.state != AppState.INSTALLED
            val levelOk = levelFilter == null || it.level == levelFilter
            val queryOk = query.isBlank() ||
                it.label.contains(query, ignoreCase = true) ||
                it.pkg.contains(query, ignoreCase = true)
            tabOk && levelOk && queryOk
        }

    fun changeTab(index: Int) {
        tab = index
        selected = emptySet()
    }

    fun toggle(item: AppItem) {
        // Komponen inti tidak boleh dipilih untuk dihapus; memulihkan selalu boleh.
        if (tab == 0 && item.level == Level.DANGER) return
        selected = if (item.pkg in selected) selected - item.pkg else selected + item.pkg
    }

    val selectedItems: List<AppItem>
        get() = items.filter { it.pkg in selected }

    // ---- eksekusi ----

    private fun commandFor(item: AppItem, restore: Boolean): String? {
        if (!Cmd.isValid(item.pkg)) return null
        return if (restore) {
            when (item.state) {
                AppState.REMOVED -> Cmd.reinstall(item.pkg)
                AppState.DISABLED -> Cmd.enable(item.pkg)
                AppState.INSTALLED -> null
            }
        } else {
            if (item.state != AppState.INSTALLED || item.level == Level.DANGER) return null
            when (method) {
                Method.UNINSTALL -> Cmd.uninstall(item.pkg)
                Method.DISABLE -> Cmd.disable(item.pkg)
            }
        }
    }

    fun execute() {
        val sh = shell ?: return
        val restore = tab == 1
        val targets = selectedItems
        showConfirm = false
        viewModelScope.launch {
            running = true
            val log = StringBuilder()
            val newHistory = history.toMutableSet()
            var okCount = 0

            withContext(Dispatchers.IO) {
                for (item in targets) {
                    val cmd = commandFor(item, restore)
                    if (cmd == null) {
                        log.appendLine("- ${item.pkg}: dilewati")
                        continue
                    }
                    if (dryRun) {
                        log.appendLine("[SIMULASI] $cmd")
                        continue
                    }
                    val result = sh.run(cmd)
                    if (result.ok) {
                        okCount++
                        if (restore) newHistory.remove(item.pkg) else newHistory.add(item.pkg)
                        log.appendLine("OK    ${item.pkg}")
                    } else {
                        log.appendLine("GAGAL ${item.pkg}: ${result.output.lineSequence().firstOrNull().orEmpty()}")
                    }
                }
            }

            if (!dryRun) {
                history = newHistory
                prefs.edit().putStringSet(KEY_HISTORY, newHistory).apply()
                log.appendLine()
                log.appendLine("Selesai: $okCount dari ${targets.size} berhasil.")
            } else {
                log.appendLine()
                log.appendLine("Mode simulasi: tidak ada yang diubah.")
            }

            logText = log.toString()
            running = false
            if (!dryRun) refresh() else selected = emptySet()
        }
    }
}

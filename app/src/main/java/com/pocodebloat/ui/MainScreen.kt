package com.pocodebloat.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocodebloat.AppItem
import com.pocodebloat.AppState
import com.pocodebloat.Level
import com.pocodebloat.MainViewModel
import com.pocodebloat.Method
import com.pocodebloat.ShizukuStatus

private fun levelColor(level: Level): Color = when (level) {
    Level.SAFE -> Color(0xFF2E7D32)
    Level.CAUTION -> Color(0xFFEF6C00)
    Level.DANGER -> Color(0xFFC62828)
    Level.UNKNOWN -> Color(0xFF616161)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Poco Debloat") }) },
        bottomBar = { if (vm.status == ShizukuStatus.READY) ActionBar(vm) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            StatusCard(vm)

            if (vm.status == ShizukuStatus.READY) {
                TabRow(selectedTabIndex = vm.tab) {
                    Tab(
                        selected = vm.tab == 0,
                        onClick = { vm.changeTab(0) },
                        text = { Text("Terpasang") }
                    )
                    Tab(
                        selected = vm.tab == 1,
                        onClick = { vm.changeTab(1) },
                        text = { Text("Dipulihkan") }
                    )
                }
                SearchAndFilters(vm)
                if (vm.loading || vm.running) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                val list = vm.visible
                if (list.isEmpty() && !vm.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (vm.tab == 0) "Tidak ada aplikasi yang cocok." else "Belum ada aplikasi yang dihapus atau dinonaktifkan.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(list, key = { it.pkg }) { item -> AppRow(item, vm) }
                    }
                }
            }
        }
    }

    if (vm.showConfirm) ConfirmDialog(vm)
    vm.logText?.let { LogDialog(it) { vm.logText = null } }
}

@Composable
private fun StatusCard(vm: MainViewModel) {
    val message = when (vm.status) {
        ShizukuStatus.NOT_INSTALLED ->
            "Shizuku belum terpasang. Pasang aplikasi Shizuku lebih dulu."
        ShizukuStatus.NOT_RUNNING ->
            "Shizuku belum berjalan. Buka Shizuku, lalu pilih Start via Wireless debugging."
        ShizukuStatus.NO_PERMISSION ->
            "Shizuku sudah berjalan, tetapi aplikasi ini belum diberi izin."
        ShizukuStatus.CONNECTING -> "Menyambung ke layanan shell..."
        ShizukuStatus.READY -> "Terhubung ke shell lewat Shizuku."
    }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            vm.hint?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (vm.status) {
                    ShizukuStatus.NOT_RUNNING -> {
                        Button(onClick = { vm.openShizuku() }) { Text("Buka Shizuku") }
                        OutlinedButton(onClick = { vm.checkStatus() }) { Text("Cek lagi") }
                    }
                    ShizukuStatus.NOT_INSTALLED ->
                        OutlinedButton(onClick = { vm.checkStatus() }) { Text("Cek lagi") }
                    ShizukuStatus.NO_PERMISSION ->
                        Button(onClick = { vm.requestPermission() }) { Text("Minta izin") }
                    ShizukuStatus.READY ->
                        OutlinedButton(onClick = { vm.refresh() }) { Text("Muat ulang") }
                    ShizukuStatus.CONNECTING -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAndFilters(vm: MainViewModel) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Cari nama atau paket") }
        )
        Row(
            Modifier
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = vm.levelFilter == null,
                onClick = { vm.levelFilter = null },
                label = { Text("Semua") }
            )
            Level.entries.forEach { level ->
                FilterChip(
                    selected = vm.levelFilter == level,
                    onClick = { vm.levelFilter = if (vm.levelFilter == level) null else level },
                    label = { Text(level.label) }
                )
            }
        }

        val context = LocalContext.current
        val clipboard = LocalClipboardManager.current
        val shown = vm.visible
        TextButton(
            onClick = {
                val text = shown.joinToString("\n") { "${it.pkg} | ${it.label} | ${it.level.label}" }
                clipboard.setText(AnnotatedString(text))
                Toast.makeText(context, "${shown.size} baris disalin", Toast.LENGTH_SHORT).show()
            },
            enabled = shown.isNotEmpty()
        ) {
            Text("Salin daftar (${shown.size})")
        }
    }
}

@Composable
private fun AppRow(item: AppItem, vm: MainViewModel) {
    val blocked = vm.tab == 0 && item.level == Level.DANGER
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !blocked) { vm.toggle(item) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.pkg in vm.selected,
                onCheckedChange = { vm.toggle(item) },
                enabled = !blocked
            )
            Column(Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.note.isNotBlank()) {
                    Text(item.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (vm.tab == 1) {
                    val stateText = when (item.state) {
                        AppState.REMOVED -> "Dihapus"
                        AppState.DISABLED -> "Nonaktif"
                        AppState.INSTALLED -> ""
                    }
                    val by = if (item.removedByApp) " (oleh aplikasi ini)" else ""
                    Text(stateText + by, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(8.dp))
            LevelBadge(item.level)
        }
        HorizontalDivider()
    }
}

@Composable
private fun LevelBadge(level: Level) {
    Box(
        Modifier
            .background(levelColor(level), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(level.label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun ActionBar(vm: MainViewModel) {
    val count = vm.selected.size
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (vm.tab == 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Method.entries.forEach { m ->
                        FilterChipCompat(selected = vm.method == m, label = m.label) { vm.method = m }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Simulasi", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = vm.dryRun, onCheckedChange = { vm.dryRun = it })
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Text("Simulasi", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = vm.dryRun, onCheckedChange = { vm.dryRun = it })
                }
            }
            Button(
                onClick = { vm.showConfirm = true },
                enabled = count > 0 && !vm.running,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                val verb = if (vm.tab == 1) "Pulihkan" else vm.method.label
                Text("$verb ($count)")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipCompat(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ConfirmDialog(vm: MainViewModel) {
    val targets = vm.selectedItems
    val restore = vm.tab == 1
    val unknown = targets.count { it.level == Level.UNKNOWN }
    val caution = targets.count { it.level == Level.CAUTION }
    val verb = if (restore) "dipulihkan" else if (vm.method == Method.UNINSTALL) "dihapus" else "dinonaktifkan"

    AlertDialog(
        onDismissRequest = { vm.showConfirm = false },
        title = { Text("${targets.size} aplikasi akan $verb") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (!restore) {
                    Text(
                        "Penghapusan hanya untuk pengguna utama (user 0) dan bisa dipulihkan lewat tab Dipulihkan. " +
                            "Reset pabrik juga mengembalikan semuanya.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (unknown > 0) {
                    Text(
                        "$unknown aplikasi belum dikenal. Pastikan kamu tahu fungsinya.",
                        color = levelColor(Level.DANGER),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (caution > 0) {
                    Text(
                        "$caution aplikasi berstatus hati-hati; sebagian fitur MIUI bisa hilang.",
                        color = levelColor(Level.CAUTION),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.padding(top = 8.dp))
                targets.forEach {
                    Text("• ${it.label}  (${it.pkg})", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.execute() }) {
                Text(if (vm.dryRun) "Jalankan simulasi" else "Lanjut")
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.showConfirm = false }) { Text("Batal") }
        }
    )
}

@Composable
private fun LogDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hasil") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

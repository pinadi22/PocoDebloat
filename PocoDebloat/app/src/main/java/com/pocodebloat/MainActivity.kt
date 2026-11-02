package com.pocodebloat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.pocodebloat.ui.MainScreen
import com.pocodebloat.ui.PocoDebloatTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocoDebloatTheme {
                MainScreen(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pengguna mungkin baru saja menyalakan Shizuku atau memberi izin
        vm.checkStatus()
    }
}

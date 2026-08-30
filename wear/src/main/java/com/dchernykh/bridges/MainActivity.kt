package com.dchernykh.bridges

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dchernykh.bridges.store.AssetBoardSource
import com.dchernykh.bridges.store.DataStoreProgressStore
import com.dchernykh.bridges.ui.BridgesApp

/**
 * The one and only activity. A watch game is a single full-screen surface with no
 * navigation to speak of, so there is nothing for a second one to do.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { DataStoreProgressStore(applicationContext) }
            val boards = remember { AssetBoardSource(applicationContext) }
            BridgesApp(viewModel(factory = BridgesViewModel.factory(store, boards)))
        }
    }
}

package com.pix.folio

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pix.folio.ui.FolioApp
import com.pix.folio.ui.V07ViewModel
import com.pix.folio.ui.theme.FolioTheme
import com.pix.folio.work.RecurringWorkScheduler

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RecurringWorkScheduler.ensureScheduled(this)

        setContent {
            val vm: V07ViewModel = viewModel()
            FolioTheme(fontChoice = vm.fontChoice) {
                FolioApp(vm)
            }
        }
    }
}

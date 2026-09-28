package uk.lunarlab.health2609

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.lunarlab.health2609.core.network.ApiFactory
import uk.lunarlab.health2609.feature.today.TodayRepository
import uk.lunarlab.health2609.feature.today.TodayScreen
import uk.lunarlab.health2609.feature.today.TodayViewModel
import uk.lunarlab.health2609.feature.today.TodayViewModelFactory
import uk.lunarlab.health2609.ui.theme.Health2609Theme

class MainActivity : ComponentActivity() {
    private val repository by lazy {
        TodayRepository(ApiFactory.create())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Health2609Theme {
                val viewModel: TodayViewModel = viewModel(
                    factory = TodayViewModelFactory(repository)
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                TodayScreen(
                    state = state,
                    onPortionChange = viewModel::setPortion,
                    onRefresh = viewModel::refresh,
                    onSave = viewModel::save
                )
            }
        }
    }
}

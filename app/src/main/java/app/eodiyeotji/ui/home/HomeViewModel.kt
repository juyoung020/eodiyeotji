package app.eodiyeotji.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.CoreStatus
import app.eodiyeotji.core.EodiCore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
sealed interface CoreUiState {
    data object Checking : CoreUiState
    data class Ready(val status: CoreStatus) : CoreUiState
    data class Failed(val message: String) : CoreUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val core: EodiCore,
) : ViewModel() {

    private val _state = MutableStateFlow<CoreUiState>(CoreUiState.Checking)
    val state: StateFlow<CoreUiState> = _state.asStateFlow()

    init {
        check()
    }

    fun check() {
        _state.value = CoreUiState.Checking
        viewModelScope.launch {
            _state.value = runCatching { core.selfCheck() }.fold(
                onSuccess = { CoreUiState.Ready(it) },
                onFailure = { CoreUiState.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }
}

package dev.zabolotskikh.authguard.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.zabolotskikh.authguard.R
import dev.zabolotskikh.authguard.domain.model.AppState
import dev.zabolotskikh.authguard.domain.model.Passcode
import dev.zabolotskikh.authguard.domain.repository.AppStateRepository
import dev.zabolotskikh.authguard.domain.repository.ServiceRepository
import dev.zabolotskikh.authguard.ui.screen.services.ServiceState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val stateRepository: AppStateRepository,
    private val serviceRepository: ServiceRepository,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    private val _appState = stateRepository.getState().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(), null
    )

    val state = combine(_appState, _state) { appState, settingsState ->
        settingsState.copy(isPasscodeEnabled = appState?.passcode != null)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState()
    )

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.BuildNumberClick -> {}
            SettingsEvent.ResetData -> resetData()
            is SettingsEvent.ChangeSection -> {
                _state.update {
                    if (it.currentSection is PreferenceSection.Passcode) it.resetPasscodeFields()
                        .copy(currentSection = event.section)
                    else it.copy(currentSection = event.section)
                }
            }

            SettingsEvent.DeletePasscode -> viewModelScope.launch(ioDispatcher) {
                _appState.value?.apply {
                    stateRepository.update(copy(passcode = null))
                }
            }

            is SettingsEvent.OnEnterPasscode -> {
                if (_state.value.passcodeSettingsAttempt == 1) {
                    viewModelScope.launch(ioDispatcher) {
                        _appState.value?.apply {
                            stateRepository.update(copy(passcode = Passcode(passcodeHash = _state.value.passcodeSettingsCurrent)))
                        }
                        delay(300)
                        println(_state.value)
                        _state.update { it.copy(currentSection = PreferenceSection.Passcode) }
//                        _state.update { it.resetPasscodeFields() }
                    }
                } else {
                    _state.update {
                        it.copy(
                            passcodeSettingsCurrent = event.passcode,
                            passcodeSettingsAttempt = it.passcodeSettingsAttempt + 1
                        )
                    }
                }
            }

            SettingsEvent.StartPasscodeSetting -> _state.update {
                it.copy(
                    passcodeSettingsProcess = true, passcodeSettingsAttempt = 0
                )
            }
        }
    }

    private fun onSettingsPasscodeClick() {
//        _state.update { it.copy(currentSection = PreferenceSection.Passcode) }
    }

    private fun resetData() = viewModelScope.launch(ioDispatcher) {
        stateRepository.update(
            AppState(
                isStarted = false, isRemoteMode = false, isPrivateMode = false, passcode = null
            )
        )
        serviceRepository.clear()
    }

    fun onNavigateBack(onNavigateBack: () -> Unit) {
        if (state.value.currentSection !is PreferenceSection.Main) {
            return _state.update {
                it.copy(currentSection = PreferenceSection.Main)
            }
        }
        onNavigateBack()
    }
}
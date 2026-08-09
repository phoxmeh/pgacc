package com.catcontroller.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catcontroller.data.ProfileRepository
import com.catcontroller.model.RadioProfile
import com.catcontroller.radio.RadioDeviceList
import com.catcontroller.serial.UsbSerialDevice
import com.catcontroller.serial.UsbSerialManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repo: ProfileRepository,
    private val usbSerialManager: UsbSerialManager,
) : ViewModel() {

    val profiles = repo.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editing = MutableStateFlow<RadioProfile?>(null)
    val editing = _editing.asStateFlow()

    private val _usbDevices = MutableStateFlow<List<UsbSerialDevice>>(emptyList())
    val usbDevices = _usbDevices.asStateFlow()

    fun refreshUsbDevices() {
        _usbDevices.value = usbSerialManager.listDevices()
    }

    fun startNew() {
        refreshUsbDevices()
        _editing.value = RadioProfile(
            name          = "New Profile",
            deviceModelId = RadioDeviceList.all.firstOrNull()?.modelId ?: 0,
            baudRate      = 9600,
        )
    }

    fun startEdit(profile: RadioProfile) {
        refreshUsbDevices()
        _editing.value = profile
    }

    fun cancelEdit() {
        _editing.value = null
    }

    fun updateEditing(block: RadioProfile.() -> RadioProfile) {
        _editing.value = _editing.value?.block()
    }

    fun saveEditing() {
        val p = _editing.value ?: return
        viewModelScope.launch {
            repo.save(p)
            _editing.value = null
        }
    }

    fun delete(profile: RadioProfile) {
        viewModelScope.launch { repo.delete(profile) }
    }

    fun setDefault(profile: RadioProfile) {
        viewModelScope.launch { repo.setDefault(profile.id) }
    }
}

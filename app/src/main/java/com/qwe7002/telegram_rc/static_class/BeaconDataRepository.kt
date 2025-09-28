package com.qwe7002.telegram_rc.static_class

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.qwe7002.telegram_rc.data_structure.BeaconModel

object BeaconDataRepository {
    private val _beaconList = MutableLiveData<ArrayList<BeaconModel.BeaconModel>>()
    val beaconList: LiveData<ArrayList<BeaconModel.BeaconModel>> = _beaconList

    private val _reloadConfig = MutableLiveData<Boolean>()
    val reloadConfig: LiveData<Boolean> = _reloadConfig

    fun updateBeaconList(list: ArrayList<BeaconModel.BeaconModel>) {
        _beaconList.postValue(list)
    }

    fun triggerReloadConfig() {
        _reloadConfig.postValue(true)
    }
    
    fun resetReloadConfig() {
        _reloadConfig.postValue(false)
    }
}
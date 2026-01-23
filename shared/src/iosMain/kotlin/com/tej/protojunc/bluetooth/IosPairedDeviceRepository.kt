package com.tej.protojunc.bluetooth

import com.tej.protojunc.models.PairedDevice
import com.tej.protojunc.models.PairedDeviceRepository

class IosPairedDeviceRepository : PairedDeviceRepository {
    override fun getPairedDevices(): List<PairedDevice> {
        return emptyList() // iOS doesn't expose bonded devices like Android
    }
}

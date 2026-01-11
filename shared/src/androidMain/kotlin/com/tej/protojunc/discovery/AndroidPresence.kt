package com.tej.protojunc.discovery

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import co.touchlab.kermit.Logger
import com.tej.protojunc.common.UserIdentity
import com.tej.protojunc.models.NearbyPeer
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID

private val PRESENCE_SERVICE_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")

class AndroidPresenceAdvertiser(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) : PresenceAdvertiser {

    private var advertiseCallback: AdvertiseCallback? = null

    override fun startAdvertising(identity: UserIdentity) {
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        // 31 bytes limit. 
        // Service UUID (16 bytes)
        // Service Data: 
        //   - 4 bytes of deviceId hash or prefix
        //   - Remaining for Name
        val shortId = identity.deviceId.take(4)
        val vaultBit = if (identity.isVaultOpen) 1 else 0
        val nameData = identity.displayName.take(10).toByteArray()
        
        val serviceData = ByteArray(shortId.length + nameData.size + 1)
        shortId.toByteArray().copyInto(serviceData)
        serviceData[shortId.length] = vaultBit.toByte()
        nameData.copyInto(serviceData, shortId.length + 1)

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(PRESENCE_SERVICE_UUID))
            .addServiceData(ParcelUuid(PRESENCE_SERVICE_UUID), serviceData)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Logger.d { "Presence Advertising Success" }
            }
            override fun onStartFailure(errorCode: Int) {
                Logger.e { "Presence Advertising Failed: $errorCode" }
            }
        }

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    override fun stopAdvertising() {
        advertiseCallback?.let {
            bluetoothAdapter.bluetoothLeAdvertiser?.stopAdvertising(it)
        }
        advertiseCallback = null
    }
}

class AndroidPresenceScanner(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) : PresenceScanner {

    override val discoveredPeers = MutableSharedFlow<NearbyPeer>(extraBufferCapacity = 50)
    private var scanCallback: ScanCallback? = null

    override fun startScanning() {
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(PRESENCE_SERVICE_UUID))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER) // Background friendly
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val serviceData = result.scanRecord?.getServiceData(ParcelUuid(PRESENCE_SERVICE_UUID)) ?: return
                
                try {
                    val idPart = serviceData.copyOfRange(0, 4).decodeToString()
                    val vaultOpen = serviceData[4].toInt() == 1
                    val namePart = serviceData.copyOfRange(5, serviceData.size).decodeToString()
                    
                    discoveredPeers.tryEmit(
                        NearbyPeer(
                            deviceId = idPart,
                            displayName = namePart,
                            lastSeen = System.currentTimeMillis(),
                            rssi = result.rssi,
                            isPaired = result.device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED
                        )
                    )
                } catch (e: Exception) {
                    Logger.w { "Failed to decode presence data" }
                }
            }
        }

        scanner.startScan(filters, settings, scanCallback)
    }

    override fun stopScanning() {
        scanCallback?.let {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(it)
        }
        scanCallback = null
    }
}

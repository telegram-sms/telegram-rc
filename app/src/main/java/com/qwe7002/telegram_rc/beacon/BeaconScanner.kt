package com.qwe7002.telegram_rc.beacon

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "BeaconScanner"
private const val EVICTION_PERIOD_MILLIS = 1_000L

interface BeaconBatchListener {
    fun onBatch(beacons: Collection<Beacon>)
}

interface IScanner {
    @RequiresPermission(
        anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    fun start()

    @RequiresPermission(
        anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    fun stop()

    val ranger: Ranger?
}

class BeaconScanner private constructor(
    context: Context,
    private val parser: BeaconParser,
    private val scanDuration: ScanDuration,
    private val expirationMillis: Long,
    private val batchListener: BeaconBatchListener?,
    override val ranger: Ranger?
) : IScanner {

    private val appContext: Context = context.applicationContext
    private val nearby = ConcurrentHashMap<String, Beacon>()
    private val running = AtomicBoolean(false)
    private val scanning = AtomicBoolean(false)

    @Volatile
    private var executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) handleScanResult(r)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed: $errorCode")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val parsed = parser.parse(result) ?: return
        val key = parsed.hardwareAddress
        val now = SystemClock.elapsedRealtime()
        val rssi = result.rssi.toByte()

        val merged = nearby.compute(key) { _, existing ->
            if (existing == null) {
                parsed.apply {
                    this.rssi = rssi
                    this.lastSeenElapsedRealtime = now
                }
            } else {
                existing.apply {
                    this.rssi = rssi
                    this.lastSeenElapsedRealtime = now
                }
            }
        } ?: return

        ranger?.record(merged, rssi)
        deliverBatch()
    }

    private fun deliverBatch() {
        val listener = batchListener ?: return
        listener.onBatch(nearby.values.toList())
    }

    @RequiresPermission(
        anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    override fun start() {
        if (!running.compareAndSet(false, true)) return
        if (executor.isShutdown) {
            executor = Executors.newSingleThreadScheduledExecutor()
        }
        executor.execute(::resumeScans)
        executor.scheduleAtFixedRate(
            ::evictExpired,
            scanDuration.scanMillis,
            EVICTION_PERIOD_MILLIS,
            TimeUnit.MILLISECONDS
        )
    }

    @RequiresPermission(
        anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        executor.execute {
            stopScansSilent()
            nearby.clear()
            ranger?.let { ranger ->
                // forget any cached filters by clearing per-beacon entries
                // (entries are weakly tied to beacon equality; clear conservatively)
                // No public clear API on Ranger by design — let GC reclaim.
            }
        }
        executor.shutdown()
    }

    private fun resumeScans() {
        if (!running.get()) return
        startScansSilent()
        executor.schedule(::pauseScans, scanDuration.scanMillis, TimeUnit.MILLISECONDS)
    }

    private fun pauseScans() {
        if (!running.get()) return
        stopScansSilent()
        executor.schedule(::resumeScans, scanDuration.restMillis, TimeUnit.MILLISECONDS)
    }

    private fun startScansSilent() {
        if (!scanning.compareAndSet(false, true)) return
        val scanner = bleScanner() ?: run {
            scanning.set(false)
            return
        }
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            scanner.startScan(parser.buildScanFilters(), settings, scanCallback)
        } catch (t: Throwable) {
            scanning.set(false)
            Log.w(TAG, "startScan failed: ${t.message}")
        }
    }

    private fun stopScansSilent() {
        if (!scanning.compareAndSet(true, false)) return
        try {
            bleScanner()?.stopScan(scanCallback)
        } catch (t: Throwable) {
            Log.w(TAG, "stopScan failed: ${t.message}")
        }
    }

    private fun bleScanner(): BluetoothLeScanner? {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        return adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
    }

    private fun evictExpired() {
        val now = SystemClock.elapsedRealtime()
        val iterator = nearby.entries.iterator()
        var changed = false
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastSeenElapsedRealtime >= expirationMillis) {
                ranger?.forget(entry.value)
                iterator.remove()
                changed = true
            }
        }
        if (changed) deliverBatch()
    }

    class Builder(context: Context) {
        private val appContext: Context = context.applicationContext
        private var parser: BeaconParser? = null
        private var scanDuration: ScanDuration = ScanDuration.UNIFORM
        private var expirationSeconds: Long = 5L
        private var batchListener: BeaconBatchListener? = null
        private var rangingEnabled: Boolean = false

        fun setBeaconParser(parser: BeaconParser) = apply { this.parser = parser }

        fun setScanDuration(duration: ScanDuration) = apply { this.scanDuration = duration }

        fun setBeaconExpirationDuration(seconds: Long) = apply {
            require(seconds >= 1) { "expiration must be >= 1 second" }
            this.expirationSeconds = seconds
        }

        fun setBeaconBatchListener(listener: BeaconBatchListener) = apply {
            this.batchListener = listener
        }

        fun setBeaconBatchListener(block: (Collection<Beacon>) -> Unit) = apply {
            this.batchListener = object : BeaconBatchListener {
                override fun onBatch(beacons: Collection<Beacon>) = block(beacons)
            }
        }

        fun setRangingEnabled() = apply { this.rangingEnabled = true }

        fun build(): IScanner {
            val parser = requireNotNull(parser) { "Beacon parser must be configured" }
            return BeaconScanner(
                context = appContext,
                parser = parser,
                scanDuration = scanDuration,
                expirationMillis = TimeUnit.SECONDS.toMillis(expirationSeconds),
                batchListener = batchListener,
                ranger = if (rangingEnabled) Ranger() else null
            )
        }
    }
}

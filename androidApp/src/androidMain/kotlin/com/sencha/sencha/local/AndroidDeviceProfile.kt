package com.sencha.sencha.local

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import com.sencha.sencha.core.domain.DeviceProfile
import com.sencha.sencha.core.domain.ModelConstraintViolation
import com.sencha.sencha.core.domain.ModelSupport

object AndroidDeviceProfileProvider {
    fun current(context: Context): DeviceProfile {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)
        val ramMb = (memoryInfo.availMem / (1024L * 1024L)).toInt()

        val statFs = StatFs(context.filesDir.absolutePath)
        val diskMb = (statFs.availableBytes / (1024L * 1024L)).toInt()

        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork
        val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        return DeviceProfile(
            availableRamMb = ramMb,
            availableDiskMb = diskMb,
            isNetworkAvailable = hasInternet && validated,
        )
    }
}

fun ModelSupport.toDisplayMessage(): String {
    val parts = violations.map { violation ->
        when (violation) {
            ModelConstraintViolation.INSUFFICIENT_RAM -> "Недостаточно RAM"
            ModelConstraintViolation.INSUFFICIENT_DISK -> "Недостаточно диска"
            ModelConstraintViolation.NETWORK_REQUIRED -> "Нужен интернет"
        }
    }
    return parts.joinToString(", ")
}

package com.sencha.sencha.core.domain

import com.sencha.sencha.core.model.ModelDescriptor


data class DeviceProfile(
    val availableRamMb: Int?,
    val availableDiskMb: Int?,
    val isNetworkAvailable: Boolean,
) {
    companion object {
        fun unknown(): DeviceProfile = DeviceProfile(
            availableRamMb = null,
            availableDiskMb = null,
            isNetworkAvailable = true,
        )
    }
}

enum class ModelConstraintViolation {
    INSUFFICIENT_RAM,
    INSUFFICIENT_DISK,
    NETWORK_REQUIRED,
}

data class ModelSupport(
    val isSupported: Boolean,
    val violations: List<ModelConstraintViolation>,
)

class ModelAvailabilityPolicy {
    fun evaluate(model: ModelDescriptor, device: DeviceProfile): ModelSupport {
        val violations = buildList {
            val minRam = model.resources.minRamMb
            val minDisk = model.resources.minDiskMb

            if (minRam != null && device.availableRamMb != null && device.availableRamMb < minRam) {
                add(ModelConstraintViolation.INSUFFICIENT_RAM)
            }
            if (minDisk != null && device.availableDiskMb != null && device.availableDiskMb < minDisk) {
                add(ModelConstraintViolation.INSUFFICIENT_DISK)
            }
            if (model.resources.requiresNetwork && !device.isNetworkAvailable) {
                add(ModelConstraintViolation.NETWORK_REQUIRED)
            }
        }

        return ModelSupport(
            isSupported = violations.isEmpty(),
            violations = violations,
        )
    }
}

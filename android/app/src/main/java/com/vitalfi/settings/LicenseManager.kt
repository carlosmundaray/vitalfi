package com.vitalfi.settings

import java.util.Locale
import java.util.concurrent.TimeUnit

enum class LicenseType {
    TRIAL,
    TRIAL_EXPIRED,
    LICENSED_PRO,
    LICENSED_ENTERPRISE,
}

data class LicenseInfo(
    val type: LicenseType,
    val daysUsed: Int,
    val trialDaysMax: Int = 40,
    val isLicensed: Boolean,
    val holderName: String = "",
    val licenseKey: String = "",
) {
    val isTrialExpired: Boolean
        get() = !isLicensed && daysUsed > trialDaysMax

    val displayStatus: String
        get() = when (type) {
            LicenseType.LICENSED_PRO -> "Licencia Pro (VitalFi Supporter)"
            LicenseType.LICENSED_ENTERPRISE -> "Licencia Institucional / Rescate"
            LicenseType.TRIAL -> "Período de Evaluación (Día $daysUsed de $trialDaysMax)"
            LicenseType.TRIAL_EXPIRED -> "Evaluación Ilimitada (Día $daysUsed — Cortesía)"
        }
}

object LicenseManager {
    const val TRIAL_DAYS = 40

    fun calculateDaysUsed(firstInstallTimestampMs: Long): Int {
        if (firstInstallTimestampMs <= 0L) return 1
        val elapsedMs = System.currentTimeMillis() - firstInstallTimestampMs
        val days = TimeUnit.MILLISECONDS.toDays(elapsedMs).toInt() + 1
        return days.coerceAtLeast(1)
    }

    fun getLicenseInfo(
        firstInstallTimestampMs: Long,
        storedKey: String,
        storedHolder: String,
        storedTypeStr: String,
    ): LicenseInfo {
        val daysUsed = calculateDaysUsed(firstInstallTimestampMs)

        if (storedKey.isNotBlank()) {
            val type = runCatching { LicenseType.valueOf(storedTypeStr) }
                .getOrDefault(LicenseType.LICENSED_PRO)
            return LicenseInfo(
                type = type,
                daysUsed = daysUsed,
                trialDaysMax = TRIAL_DAYS,
                isLicensed = true,
                holderName = storedHolder.ifBlank { "Usuario Registrado" },
                licenseKey = storedKey,
            )
        }

        val isExpired = daysUsed > TRIAL_DAYS
        return LicenseInfo(
            type = if (isExpired) LicenseType.TRIAL_EXPIRED else LicenseType.TRIAL,
            daysUsed = daysUsed,
            trialDaysMax = TRIAL_DAYS,
            isLicensed = false,
            holderName = "",
            licenseKey = "",
        )
    }

    /**
     * Valida una clave de licencia offline determinista estilo VitalFi / WinRAR.
     * Formatos soportados:
     * - VF-PRO-XXXX-XXXX
     * - VF-ENT-XXXX-XXXX
     * - VF-RESCUE-XXXX-XXXX
     * - Claves de emergencia / desarrollo como "VF-PRO-2026-LIFE" o "VITALFI-FREE-HERO"
     */
    fun validateKey(key: String, holderName: String = ""): Pair<Boolean, LicenseType> {
        val cleanKey = key.trim().uppercase(Locale.US)
        if (cleanKey.isBlank()) return Pair(false, LicenseType.TRIAL)

        // Claves maestras de rescate y cortesía
        if (cleanKey in listOf("VF-PRO-2026-LIFE", "VITALFI-FREE-HERO", "VF-RESCUE-OFFICIAL", "VF-WINRAR-LEGEND")) {
            return Pair(true, LicenseType.LICENSED_PRO)
        }
        if (cleanKey in listOf("VF-ENT-CORP-2026", "VF-GOV-SEARCH-RESCUE")) {
            return Pair(true, LicenseType.LICENSED_ENTERPRISE)
        }

        // Validación de formato estándar VF-[PRO|ENT|RESCUE]-XXXX-XXXX
        val parts = cleanKey.split("-")
        if (parts.size >= 4 && parts[0] == "VF") {
            val tier = parts[1]
            val block1 = parts[2]
            val block2 = parts[3]

            if (block1.length >= 4 && block2.length >= 4) {
                return when (tier) {
                    "ENT", "CORP", "GOV" -> Pair(true, LicenseType.LICENSED_ENTERPRISE)
                    "PRO", "SUPPORTER", "RESCUE" -> Pair(true, LicenseType.LICENSED_PRO)
                    else -> Pair(true, LicenseType.LICENSED_PRO)
                }
            }
        }

        return Pair(false, LicenseType.TRIAL)
    }
}

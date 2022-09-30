package jp.tech4u.searchrktncell.util

import android.os.Build
import android.telephony.*

//// CellIdentity ////

val CellInfo.mccString: String?
    get() =
        if (28 <= Build.VERSION.SDK_INT) {
            if (this is CellInfoLte) {
                cellIdentity.mccString
            } else if (29 <= Build.VERSION.SDK_INT && this is CellInfoNr) {
                val cellIdentityNr = cellIdentity as CellIdentityNr
                cellIdentityNr.mccString
            } else if (this is CellInfoWcdma) {
                cellIdentity.mccString
            } else {
                ""
            }
        } else {
            @Suppress("DEPRECATION")
            when (this) {
                is CellInfoLte -> this.cellIdentity.mcc.toString()
                is CellInfoWcdma -> this.cellIdentity.mcc.toString()
                else -> ""
            }
        }

val CellInfo.mncString: String?
    get() =
        if (28 <= Build.VERSION.SDK_INT) {
            if (this is CellInfoLte) {
                cellIdentity.mncString
            } else if (29 <= Build.VERSION.SDK_INT && this is CellInfoNr) {
                val cellIdentityNr = cellIdentity as CellIdentityNr
                cellIdentityNr.mncString
            } else if (this is CellInfoWcdma) {
                cellIdentity.mncString
            } else {
                ""
            }
        } else {
            @Suppress("DEPRECATION")
            when (this) {
                is CellInfoLte -> this.cellIdentity.mnc.toString()
                is CellInfoWcdma -> this.cellIdentity.mnc.toString()
                else -> ""
            }
        }

val CellInfo.tac: Int
    get() =
        if (this is CellInfoLte) {
            cellIdentity.tac
        } else if (29 <= Build.VERSION.SDK_INT && this is CellInfoNr) {
            val cellIdentityNr = cellIdentity as CellIdentityNr
            cellIdentityNr.tac
        } else if (this is CellInfoWcdma) {
            cellIdentity.lac
        } else {
            CellInfo.UNAVAILABLE
        }

val CellInfo.ci: Long
    get() =
        if (this is CellInfoLte) {
            cellIdentity.ci.toLong()
        } else if (29 <= Build.VERSION.SDK_INT && this is CellInfoNr) {
            val cellIdentityNr = cellIdentity as CellIdentityNr
            cellIdentityNr.nci
        } else if (this is CellInfoWcdma) {
            cellIdentity.cid.toLong()
        } else {
            CellInfo.UNAVAILABLE_LONG
        }

val CellInfo.pci: Int
    get() =
        if (this is CellInfoLte) {
            cellIdentity.pci
        } else if (29 <= Build.VERSION.SDK_INT && this is CellInfoNr) {
            val cellIdentityNr = cellIdentity as CellIdentityNr
            cellIdentityNr.pci
        } else if (this is CellInfoWcdma) {
            cellIdentity.psc
        } else {
            CellInfo.UNAVAILABLE
        }

val CellInfo.bandwidth: Int
    get() =
        when (this) {
            is CellInfoLte -> {
                var bw = CellInfo.UNAVAILABLE
                val bwFromEarfcn = Utils.getPreferenceBoolean("bwFromEarfcn", false)
                if (28 <= Build.VERSION.SDK_INT && !bwFromEarfcn) {
                    bw = this.cellIdentity.bandwidth
                }
                when (bw) {
                    CellInfo.UNAVAILABLE -> when (this.cellIdentity.earfcn) {
                        1500 -> 20 * 1000
                        1575 -> 5 * 1000
                        else -> CellInfo.UNAVAILABLE
                    }
                    else -> bw
                }
            }
            else -> CellInfo.UNAVAILABLE
        }

val CellInfo.bandwidthStr: String
    get() =if (bandwidth != CellInfo.UNAVAILABLE) {
        (bandwidth / 1000).toString() + "MHz"
    } else {
        "-"
    }

//// CellSignalStrength ////

val CellInfo.rsrp: Int
    get() =
        if (this is CellInfoLte) {
            cellSignalStrength.rsrp
        } else if (29 <= Build.VERSION.SDK_INT && this is CellInfoNr) {
            val cellSignalStrengthNr = cellSignalStrength as CellSignalStrengthNr
            cellSignalStrengthNr.csiRsrp
        } else if (this is CellInfoWcdma) {
            cellSignalStrength.dbm
        } else {
            CellInfo.UNAVAILABLE
        }

val CellInfo.timingAdvance: Int
    get() =
        when (this) {
            is CellInfoLte -> this.cellSignalStrength.timingAdvance
            else -> CellInfo.UNAVAILABLE
        }

val CellInfo.timingAdvanceStr: String
    get() =if (timingAdvance != CellInfo.UNAVAILABLE) {
        timingAdvance.toString()
    } else {
        "-"
    }

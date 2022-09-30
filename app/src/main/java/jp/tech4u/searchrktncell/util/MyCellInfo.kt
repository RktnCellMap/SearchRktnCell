package jp.tech4u.searchrktncell.util

import android.os.Build
import android.telephony.*

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
            when (this) {
                is CellInfoLte -> this.cellIdentity.mnc.toString()
                is CellInfoWcdma -> this.cellIdentity.mnc.toString()
                else -> ""
            }
        }

val CellInfo.bandwidth: Int
    get() =
        when (this) {
            is CellInfoLte -> when {
                28 <= Build.VERSION.SDK_INT -> this.cellIdentity.bandwidth
                else -> when (this.cellIdentity.earfcn) {
                    1500 -> 20*1000
                    1575 -> 5 * 1000
                    else -> CellInfo.UNAVAILABLE
                }
            }
            else -> CellInfo.UNAVAILABLE
        }

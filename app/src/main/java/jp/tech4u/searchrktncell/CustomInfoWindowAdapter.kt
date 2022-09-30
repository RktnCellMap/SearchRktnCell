package jp.tech4u.searchrktncell

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter
import com.google.android.gms.maps.model.Marker
import jp.tech4u.searchrktncell.data.CellId
import jp.tech4u.searchrktncell.data.LogVO
import jp.tech4u.searchrktncell.util.RmCellClassification

// 軌跡クリック時のポップアップ
class CustomInfoWindowAdapter(newActivity: Activity) : InfoWindowAdapter {
    private val trailInfoContents: View = newActivity.layoutInflater.inflate(R.layout.trail_info_contents, null)

    override fun getInfoWindow(marker: Marker): View? {
        return null;
    }

    override fun getInfoContents(marker: Marker): View? {
        return if (marker.tag is LogVO) {
            renderTrailInfoContents(marker.tag as LogVO, trailInfoContents)
            trailInfoContents
        } else {
            null
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderTrailInfoContents(logVO: LogVO, view: View) {
        val txtTitle = view.findViewById(R.id.title) as TextView
        val txtSnippet1 = view.findViewById(R.id.snippet1) as TextView
        val txtSnippet2 = view.findViewById(R.id.snippet2) as TextView
        val txtSnippet3 = view.findViewById(R.id.snippet3) as TextView

        val ci = CellId(logVO.ci)

        val enbInfo = RmCellClassification.getEnbInfo(ci.eNB)
        val tacInfo = RmCellClassification.getTacInfo(logVO.tac)
        val pciInfo = RmCellClassification.getPciInfo(logVO.pci)

        txtTitle.text = "${ci.eNB}-${ci.sector} (${enbInfo.first})"
        txtSnippet1.text = "TAC:${logVO.tac} (${tacInfo.first})"
        txtSnippet2.text = "PCI:${logVO.pci} (${pciInfo.first})"
        txtSnippet3.text = "RSRP:${logVO.rsrp} TA:${logVO.taStr()}"
    }
}
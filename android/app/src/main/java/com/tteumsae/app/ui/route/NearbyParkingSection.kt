package com.tteumsae.app.ui.route

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.tteumsae.app.domain.NearbyParkingLot
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing

internal fun parkingAccessCaution(name: String): String =
    if (name.contains("거주자우선"))
        "명칭에 거주자우선 표시가 있어요. 방문객 이용 가능 여부를 확인해 주세요."
    else "실시간 빈자리 정보가 아니에요. 방문객 이용 가능 여부와 요금은 현장에서 확인해 주세요."

@Composable
internal fun NearbyParkingSection(parkingLots: List<NearbyParkingLot>) {
    if (parkingLots.isEmpty()) return
    var expanded by remember(parkingLots) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(TteumSpacing.sm)) {
        Text("주변 공영주차장", style = MaterialTheme.typography.titleLarge)
        Text("직선거리 기준이며 실제 이동 경로와 달라요", color = TteumMuted, style = MaterialTheme.typography.bodySmall)
        parkingLots.take(if (expanded) parkingLots.size else 1).forEach { parking ->
            HorizontalDivider()
            Text(parking.name, style = MaterialTheme.typography.titleMedium)
            Text(parkingAccessCaution(parking.name), color = TteumMuted, style = MaterialTheme.typography.bodySmall)
            Text(nearbyParkingDescription(parking), style = MaterialTheme.typography.bodyMedium)
        }
        if (parkingLots.size > 1) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = TteumSizing.touchTarget), shape = TteumShapes.control, contentPadding = TteumInsets.button) {
                Text(if (expanded) "주차장 접기" else "주차장 ${parkingLots.size - 1}곳 더 보기")
            }
        }
    }
}

package com.tteumsae.app.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.ui.route.nearbyParkingDescription
import com.tteumsae.app.ui.route.normalizedVisitInfo
import com.tteumsae.app.ui.route.placePhotoMayCrop
import com.tteumsae.app.ui.route.placePhotoSourceCaption
import com.tteumsae.app.ui.route.placeSourceCaption
import com.tteumsae.app.ui.route.plainTourText
import com.tteumsae.app.ui.route.practicalVisitFacts
import com.tteumsae.app.ui.theme.TteumBackground
import com.tteumsae.app.ui.theme.TteumInk
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumRed
import com.tteumsae.app.ui.theme.TteumRedSoft
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing

private val SavedDetailMaxWidth = 720.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SavedPlaceDetailScreen(
    place: PlaceCandidate,
    isSaved: Boolean,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val address = normalizedVisitInfo(place.address)
    val overview = plainTourText(place.overview)
    val visitFacts = practicalVisitFacts(place)
    val tags = place.tags.mapNotNull(::normalizedVisitInfo).distinct()
    val heroImageUrl = normalizedVisitInfo(place.imageUrl)
        ?: place.imageUrls.firstNotNullOfOrNull(::normalizedVisitInfo)
        ?: ""
    val heroHeight = if (heroImageUrl.isBlank()) 164.dp else 232.dp
    val photoSourceCaption = placePhotoSourceCaption(place, heroImageUrl)
    val additionalDetails = place.detailItems.mapNotNull { item ->
        val title = normalizedVisitInfo(item.title)
        val description = plainTourText(item.description)
        if (title == null || description == null) null else title to description
    }.distinct()
    val accessibilityDetails = place.accessibilityItems.mapNotNull { item ->
        val title = normalizedVisitInfo(item.title)
        val description = plainTourText(item.description)
        if (title == null || description == null) null else title to description
    }.distinct()

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = TteumSpacing.screenHorizontal, vertical = TteumSpacing.itemGap),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        contentPadding = TteumInsets.button,
                        onClick = onOpenMap,
                        modifier = Modifier
                            .widthIn(max = SavedDetailMaxWidth)
                            .fillMaxWidth()
                            .heightIn(min = TteumSizing.buttonHeight),
                        shape = TteumShapes.control,
                        colors = ButtonDefaults.buttonColors(containerColor = TteumRed),
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null)
                        Spacer(Modifier.width(TteumSpacing.inlineGap))
                        Text("카카오맵에서 보기", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = TteumSpacing.sectionGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Box(
                    modifier = Modifier
                        .widthIn(max = SavedDetailMaxWidth)
                        .fillMaxWidth()
                        .height(heroHeight),
                ) {
                    SavedPlaceImage(
                        imageUrl = heroImageUrl,
                        category = place.category,
                        cropAllowed = placePhotoMayCrop(place, heroImageUrl),
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(TteumSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.background(
                                Color.White.copy(alpha = 0.94f),
                                CircleShape,
                            ),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                        }
                        IconButton(
                            onClick = onToggleSave,
                            modifier = Modifier.background(
                                Color.White.copy(alpha = 0.94f),
                                CircleShape,
                            ),
                        ) {
                            Icon(
                                if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isSaved) "저장 해제" else "저장",
                                tint = if (isSaved) TteumRed else TteumMuted,
                            )
                        }
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .widthIn(max = SavedDetailMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = TteumSpacing.screenHorizontal, vertical = TteumSpacing.sectionGap),
                ) {
                    photoSourceCaption?.let { caption ->
                        TourPhotoAttribution(
                            caption = caption,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(TteumSpacing.sm))
                    }
                    Surface(
                        color = TteumRedSoft,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            place.category.label,
                            modifier = Modifier.padding(horizontal = TteumSpacing.sm, vertical = TteumSpacing.xxs),
                            color = TteumRed,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(Modifier.height(TteumSpacing.sm))
                    Text(
                        place.name,
                        color = TteumInk,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    address?.let {
                        Spacer(Modifier.height(TteumSpacing.inlineGap))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = TteumMuted,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(18.dp),
                            )
                            Spacer(Modifier.width(TteumSpacing.xs))
                            Text(
                                it,
                                modifier = Modifier.weight(1f),
                                color = TteumMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    overview?.let {
                        Spacer(Modifier.height(TteumSpacing.sectionGap))
                        SavedDetailSectionTitle("장소 소개")
                        Spacer(Modifier.height(TteumSpacing.inlineGap))
                        Text(
                            it,
                            color = Color(0xFF4F5560),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    if (visitFacts.isNotEmpty()) {
                        Spacer(Modifier.height(TteumSpacing.sectionGap))
                        SavedDetailSectionTitle("방문 전 확인")
                        Spacer(Modifier.height(TteumSpacing.sm))
                        Column(verticalArrangement = Arrangement.spacedBy(TteumSpacing.inlineGap)) {
                            visitFacts.forEach { fact ->
                                SavedDetailInfoRow(label = fact.label, value = fact.value)
                            }
                        }
                    }

                    if (additionalDetails.isNotEmpty()) {
                        Spacer(Modifier.height(TteumSpacing.sectionGap))
                        SavedDetailSectionTitle("추가 안내")
                        Spacer(Modifier.height(TteumSpacing.sm))
                        Column(verticalArrangement = Arrangement.spacedBy(TteumSpacing.inlineGap)) {
                            additionalDetails.forEach { (title, description) ->
                                SavedDetailInfoRow(label = title, value = description)
                            }
                        }
                    }
                    if (accessibilityDetails.isNotEmpty()) {
                        Spacer(Modifier.height(TteumSpacing.sectionGap))
                        SavedDetailSectionTitle("접근성 안내")
                        Spacer(Modifier.height(TteumSpacing.sm))
                        Column(verticalArrangement = Arrangement.spacedBy(TteumSpacing.inlineGap)) {
                            accessibilityDetails.forEach { (title, description) ->
                                SavedDetailInfoRow(label = title, value = description)
                            }
                        }
                    }
                    if (place.nearbyParkingLots.isNotEmpty()) {
                        Spacer(Modifier.height(TteumSpacing.sectionGap))
                        SavedDetailSectionTitle("주변 공영주차장")
                        Spacer(Modifier.height(TteumSpacing.sm))
                        Column(verticalArrangement = Arrangement.spacedBy(TteumSpacing.inlineGap)) {
                            place.nearbyParkingLots.forEach { parking ->
                                SavedDetailInfoRow(
                                    label = parking.name,
                                    value = nearbyParkingDescription(parking),
                                )
                            }
                        }
                    }

                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(TteumSpacing.sectionGap))
                        SavedDetailSectionTitle("장소 특징")
                        Spacer(Modifier.height(TteumSpacing.sm))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(TteumSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(TteumSpacing.xs),
                        ) {
                            tags.forEach { tag ->
                                Surface(
                                    color = Color(0xFFF1F2F4),
                                    shape = TteumShapes.badge,
                                ) {
                                    Text(
                                        tag,
                                        modifier = Modifier.padding(
                                            horizontal = TteumSpacing.sm,
                                            vertical = TteumSpacing.xs,
                                        ),
                                        color = Color(0xFF55585F),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(TteumSpacing.sectionGap))
                    Text(
                        placeSourceCaption(place),
                        color = TteumMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedDetailSectionTitle(text: String) {
    Text(
        text,
        color = TteumInk,
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun SavedDetailInfoRow(
    label: String,
    value: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TteumBackground,
        shape = TteumShapes.compact,
    ) {
        Row(
            modifier = Modifier.padding(TteumInsets.row),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                label,
                modifier = Modifier.width(72.dp),
                color = TteumInk,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                value,
                modifier = Modifier.weight(1f),
                color = TteumMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

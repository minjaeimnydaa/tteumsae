package com.tteumsae.app.ui.saved

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.platform.openPolicy
import com.tteumsae.app.platform.savedImageCache
import com.tteumsae.app.ui.common.compactTags
import com.tteumsae.app.ui.route.TOUR_PHOTO_USAGE_CONDITIONS_URL
import com.tteumsae.app.ui.route.placePhotoMayCrop
import com.tteumsae.app.ui.theme.TteumBackground
import com.tteumsae.app.ui.theme.TteumInk
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumRed
import com.tteumsae.app.ui.theme.TteumRedSoft
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

private enum class SavedPhotoLoadState { NO_PHOTO, LOADING, READY, FAILED }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SavedFilterChip(
    text: String,
    selected: Boolean,
    showHeart: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = TteumSizing.touchTarget)
            .selectable(
                selected = selected,
                role = Role.Checkbox,
                onClick = onClick,
            ),
        color = if (selected) TteumInk else Color(0xFFF4F5F7),
        shape = RoundedCornerShape(50),
        border = if (selected) null else BorderStroke(1.dp, Color(0xFFE1E3E8)),
    ) {
        Row(
            modifier = Modifier.padding(TteumInsets.row),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                color = if (selected) Color.White else Color(0xFF596170),
                style = MaterialTheme.typography.labelMedium,
            )
            if (showHeart) {
                Spacer(Modifier.width(TteumSpacing.xxs))
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = TteumRed,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SavedPlaceCard(
    place: PlaceCandidate,
    isSaved: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
) {
    val (visibleTags, hiddenTagCount) = compactTags(place.tags)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = TteumShapes.card,
    ) {
        Column {
            Box {
                SavedPlaceImage(
                    imageUrl = place.imageUrl,
                    category = place.category,
                    cropAllowed = placePhotoMayCrop(place, place.imageUrl),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp),
                )
                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(TteumSpacing.inlineGap)
                        .background(Color.White.copy(alpha = 0.92f), CircleShape),
                ) {
                    Icon(
                        if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isSaved) "저장 해제" else "저장",
                        tint = if (isSaved) TteumRed else TteumMuted,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(TteumInsets.card),
            ) {
                Text(
                    place.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(TteumSpacing.xxs))
                Text(
                    place.category.label,
                    color = Color(0xFF55585F),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (place.address.isNotBlank()) {
                    Spacer(Modifier.height(TteumSpacing.inlineGap))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = TteumMuted,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(TteumSpacing.xxs))
                        Text(
                            place.address,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = TteumMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                val compactItems = visibleTags +
                    if (hiddenTagCount > 0) listOf("+${hiddenTagCount}개") else emptyList()
                if (compactItems.isNotEmpty()) {
                    Spacer(Modifier.height(TteumSpacing.sm))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(TteumSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(TteumSpacing.xs),
                    ) {
                        compactItems.forEach { tag ->
                            Surface(
                                color = Color(0xFFF1F2F4),
                                shape = TteumShapes.badge,
                            ) {
                                Text(
                                    tag,
                                    modifier = Modifier
                                        .widthIn(max = 124.dp)
                                        .padding(horizontal = TteumSpacing.xs, vertical = TteumSpacing.xxs),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color(0xFF55585F),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SavedPlaceImage(
    imageUrl: String,
    category: PlaceCategory? = null,
    cropAllowed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(imageUrl) {
        mutableStateOf(savedImageCache.get(imageUrl)?.asImageBitmap())
    }
    var photoState by remember(imageUrl) {
        mutableStateOf(
            when {
                imageUrl.isBlank() -> SavedPhotoLoadState.NO_PHOTO
                bitmap != null -> SavedPhotoLoadState.READY
                else -> SavedPhotoLoadState.LOADING
            },
        )
    }
    var retryCount by rememberSaveable(imageUrl) { mutableStateOf(0) }
    LaunchedEffect(imageUrl, retryCount) {
        if (imageUrl.isBlank()) return@LaunchedEffect
        if (bitmap == null) photoState = SavedPhotoLoadState.LOADING
        val loadedBitmap = try {
            withContext(Dispatchers.IO) {
                savedImageCache.get(imageUrl)?.asImageBitmap() ?: run {
                    val connection = URL(imageUrl).openConnection().apply {
                        connectTimeout = 8_000
                        readTimeout = 8_000
                    }
                    connection.getInputStream().use { BitmapFactory.decodeStream(it) }
                        ?.also { savedImageCache.put(imageUrl, it) }
                        ?.asImageBitmap()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        bitmap = loadedBitmap
        photoState = if (loadedBitmap == null) SavedPhotoLoadState.FAILED else SavedPhotoLoadState.READY
    }

    if (photoState == SavedPhotoLoadState.READY && bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            contentScale = if (cropAllowed) ContentScale.Crop else ContentScale.Fit,
            modifier = if (cropAllowed) modifier else modifier.background(TteumBackground),
        )
    } else if (photoState == SavedPhotoLoadState.LOADING) {
        Box(
            modifier = modifier.background(TteumBackground),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), color = TteumMuted)
        }
    } else if (photoState == SavedPhotoLoadState.FAILED) {
        Box(
            modifier = modifier.background(TteumBackground),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("사진 다시 시도", color = TteumMuted, style = MaterialTheme.typography.bodySmall)
                IconButton(
                    onClick = {
                        savedImageCache.remove(imageUrl)
                        bitmap = null
                        photoState = SavedPhotoLoadState.LOADING
                        retryCount += 1
                    },
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "사진 다시 불러오기", tint = TteumMuted)
                }
            }
        }
    } else {
        val placeholderColor = when (category) {
            PlaceCategory.ATTRACTION -> Color(0xFFFFE5EA)
            PlaceCategory.RESTAURANT -> Color(0xFFFFEBDD)
            PlaceCategory.CAFE -> Color(0xFFF4E7DB)
            PlaceCategory.CULTURE -> Color(0xFFEAE5F8)
            PlaceCategory.FESTIVAL -> Color(0xFFFFF0C9)
            PlaceCategory.SHOPPING -> Color(0xFFE2ECFA)
            PlaceCategory.LEISURE -> Color(0xFFE1F1E8)
            null -> TteumRedSoft
        }
        Box(
            modifier = modifier.background(placeholderColor),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = TteumRed,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.height(TteumSpacing.xxs))
                Text(category?.label ?: "틈새", color = TteumRed, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
internal fun TourPhotoAttribution(caption: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        Text(caption, color = TteumMuted, style = MaterialTheme.typography.bodySmall)
        TextButton(
            shape = TteumShapes.control,
            contentPadding = TteumInsets.button,
            onClick = { openPolicy(context, TOUR_PHOTO_USAGE_CONDITIONS_URL) },
            modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
        ) {
            Text("사진 출처·이용조건 보기", style = MaterialTheme.typography.labelMedium)
        }
    }
}

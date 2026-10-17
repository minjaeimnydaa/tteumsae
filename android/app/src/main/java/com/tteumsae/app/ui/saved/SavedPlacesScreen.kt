package com.tteumsae.app.ui.saved

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.recommendation.matchesGangwonRegion
import com.tteumsae.app.domain.saved.SavedPlace
import com.tteumsae.app.ui.BottomNavigation
import com.tteumsae.app.ui.navigation.MainTab
import com.tteumsae.app.ui.theme.TteumBackground
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumRed
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing
import kotlinx.coroutines.launch

private enum class SavedSort {
    DEFAULT,
    NAME,
}

private val SavedCardMinWidth = TteumSizing.placeCardMinWidth

private data class PlaceCardItem(
    val place: PlaceCandidate,
    val savedEntry: SavedPlace?,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SavedPlacesScreen(
    catalogPlaces: List<PlaceCandidate>,
    savedPlaces: List<SavedPlace>,
    selectedRegion: String,
    regions: List<String>,
    onRegionSelected: (String) -> Unit,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    loadMoreFailed: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onToggleSave: (PlaceCandidate) -> Unit,
    onRestore: (SavedPlace) -> Unit,
    onOpenMap: (PlaceCandidate) -> Unit,
    onTabSelected: (MainTab) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible
    var selectedCategory by rememberSaveable { mutableStateOf<PlaceCategory?>(null) }
    var regionMenuExpanded by remember { mutableStateOf(false) }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SavedSort.DEFAULT) }
    var searchFiltersExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val cardMinWidth = catalogCardMinWidthDp(SavedCardMinWidth.value, LocalDensity.current.fontScale).dp
    val compactSearchHeader = isImeVisible || query.isNotBlank()
    val showBrowseControls = !compactSearchHeader || searchFiltersExpanded

    BackHandler(enabled = isImeVisible && selectedPlaceId == null) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    BackHandler(enabled = selectedPlaceId != null) { selectedPlaceId = null }

    val savedById = savedPlaces.associateBy { it.place.id }
    val catalogIds = catalogPlaces.mapTo(mutableSetOf()) { it.id }
    val allPlaces = (catalogPlaces + savedPlaces.map { it.place }).distinctBy { it.id }
    val selectedPlace = allPlaces.firstOrNull { it.id == selectedPlaceId }

    selectedPlace?.let { place ->
        SavedPlaceDetailScreen(
            place = place,
            isSaved = place.id in savedById,
            onBack = { selectedPlaceId = null },
            onToggleSave = { onToggleSave(place) },
            onOpenMap = { onOpenMap(place) },
        )
        return
    }

    val visiblePlaces = allPlaces
        .asSequence()
        .filter { it.id in catalogIds || matchesGangwonRegion(it.address, selectedRegion) }
        .filter { !savedOnly || it.id in savedById }
        .filter { selectedCategory == null || it.category == selectedCategory }
        .filter { place ->
            query.isBlank() ||
                place.name.contains(query, ignoreCase = true) ||
                place.address.contains(query, ignoreCase = true)
        }
        .map { catalogPlace ->
            val savedEntry = savedById[catalogPlace.id]
            PlaceCardItem(
                place = if (savedEntry == null) {
                    catalogPlace
                } else {
                    catalogPlace.copy(tags = savedEntry.place.tags)
                },
                savedEntry = savedEntry,
            )
        }
        .let { entries ->
            when (sort) {
                SavedSort.DEFAULT -> entries
                SavedSort.NAME -> entries.sortedBy { it.place.name }
            }
        }
        .toList()
    val hasActiveSearchOrFilter = query.isNotBlank() || savedOnly || selectedCategory != null
    val noSavedPlaces = savedOnly && savedPlaces.isEmpty() && query.isBlank() && selectedCategory == null
    val catalogState = catalogUiState(
        savedOnly = savedOnly,
        visibleCount = visiblePlaces.size,
        initialLoading = isLoading,
        initialFailed = errorMessage != null,
        hasMore = hasMore,
        loadingMore = isLoadingMore,
        loadMoreFailed = loadMoreFailed,
    )
    val shouldLoadMore by remember(
        savedOnly,
        visiblePlaces.size,
        hasMore,
        loadMoreFailed,
        isLoading,
        isLoadingMore,
        errorMessage,
    ) {
        derivedStateOf {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            shouldLoadCatalogMore(
                savedOnly = savedOnly,
                visibleCount = visiblePlaces.size,
                initialLoading = isLoading,
                initialFailed = errorMessage != null,
                hasMore = hasMore,
                loadingMore = isLoadingMore,
                loadMoreFailed = loadMoreFailed,
                nearListEnd = lastVisibleIndex >= visiblePlaces.lastIndex - 6,
            )
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    Scaffold(
        containerColor = TteumBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isImeVisible) {
                BottomNavigation(
                    selectedTab = MainTab.SAVED,
                    onTabSelected = onTabSelected,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(top = TteumSpacing.itemGap, bottom = TteumSpacing.md),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("장소명·주소 검색", color = TteumMuted, style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotBlank()) {
                        {
                            IconButton(
                                onClick = {
                                    query = ""
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                },
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "검색어 지우기")
                            }
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        },
                    ),
                    singleLine = true,
                    shape = TteumShapes.control,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TteumSpacing.screenHorizontal)
                        .heightIn(min = TteumSizing.inputMinHeight)
                        .shadow(8.dp, TteumShapes.control),
                )
                if (compactSearchHeader) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TteumSpacing.screenHorizontal),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            listOfNotNull(selectedRegion, selectedCategory?.label, "찜".takeIf { savedOnly }).joinToString(" · "),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = TteumMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(
                            onClick = { searchFiltersExpanded = !searchFiltersExpanded },
                            shape = TteumShapes.control,
                            contentPadding = TteumInsets.button,
                        ) {
                            Text(
                                if (searchFiltersExpanded) "필터 접기" else "필터 보기",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                if (showBrowseControls) {
                    Spacer(Modifier.height(if (compactSearchHeader) TteumSpacing.xxs else TteumSpacing.lg))
                    Box(modifier = Modifier.padding(horizontal = TteumSpacing.screenHorizontal)) {
                        Row(
                            modifier = Modifier
                                .clickable { regionMenuExpanded = true }
                                .heightIn(min = TteumSizing.touchTarget)
                                .padding(vertical = TteumSpacing.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                selectedRegion,
                                style = if (compactSearchHeader) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                            )
                            Spacer(Modifier.width(TteumSpacing.xxs))
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = "지역 선택",
                                tint = TteumMuted,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = regionMenuExpanded,
                            onDismissRequest = { regionMenuExpanded = false },
                        ) {
                            regions.forEach { region ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            region,
                                            style = if (selectedRegion == region) {
                                                MaterialTheme.typography.labelLarge
                                            } else {
                                                MaterialTheme.typography.bodyLarge
                                            },
                                        )
                                    },
                                    onClick = {
                                        onRegionSelected(region)
                                        regionMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(TteumSpacing.md))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = TteumSpacing.screenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(TteumSpacing.inlineGap),
                    ) {
                        item {
                            SavedFilterChip(
                                text = "전체",
                                selected = selectedCategory == null && !savedOnly,
                                onClick = {
                                    selectedCategory = null
                                    savedOnly = false
                                },
                            )
                        }
                        item {
                            SavedFilterChip(
                                text = "찜",
                                selected = savedOnly,
                                showHeart = true,
                                onClick = { savedOnly = !savedOnly },
                            )
                        }
                        items(PlaceCategory.entries) { category ->
                            SavedFilterChip(
                                text = when (category) {
                                    PlaceCategory.CULTURE -> "문화/예술"
                                    PlaceCategory.FESTIVAL -> "행사/공연/축제"
                                    else -> category.label
                                },
                                selected = selectedCategory == category,
                                onClick = {
                                    selectedCategory = category.takeUnless { it == selectedCategory }
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TteumSpacing.screenHorizontal, vertical = TteumSpacing.itemGap),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (catalogState) {
                            CatalogUiState.LOADING, CatalogUiState.SEARCHING -> "찾는 중"
                            else -> "표시 중 ${visiblePlaces.size}곳"
                        },
                        color = TteumMuted,
                        style = MaterialTheme.typography.titleSmall,
                    )

                }
                TextButton(
                    shape = TteumShapes.control,
                    contentPadding = TteumInsets.button,
                    onClick = {
                        sort = if (sort == SavedSort.DEFAULT) SavedSort.NAME else SavedSort.DEFAULT
                    },
                ) {
                    Text(
                        if (sort == SavedSort.DEFAULT) "기본순" else "이름순",
                        color = TteumMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            if (!savedOnly && catalogState == CatalogUiState.RESULTS) {
                Text(
                    if (hasMore || isLoadingMore || loadMoreFailed) catalogContinuationLabel(hasMore, isLoadingMore, loadMoreFailed)
                    else "불러올 장소를 모두 표시했어요",
                    modifier = Modifier.fillMaxWidth().padding(start = TteumSpacing.screenHorizontal, end = TteumSpacing.screenHorizontal, bottom = TteumSpacing.itemGap),
                    color = TteumMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (catalogState == CatalogUiState.LOADING) {
                SavedPlacesLoadingGrid(cardMinWidth)
            } else if (catalogState == CatalogUiState.INITIAL_FAILURE) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(TteumSpacing.screenHorizontal),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(errorMessage.orEmpty(), color = TteumMuted, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(TteumSpacing.sm))
                    OutlinedButton(
                        shape = TteumShapes.control,
                        contentPadding = TteumInsets.button,
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                    ) {
                        Text("다시 불러오기", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else if (catalogState == CatalogUiState.SEARCHING || catalogState == CatalogUiState.CONTINUATION_FAILURE) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(TteumSpacing.screenHorizontal),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (catalogState == CatalogUiState.SEARCHING) {
                        CircularProgressIndicator(Modifier.size(TteumSizing.touchTarget))
                        Spacer(Modifier.height(TteumSpacing.md))
                        Text("다른 장소도 찾고 있어요", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(TteumSpacing.xs))
                        Text("검색 조건을 유지하며 나머지 장소를 확인하고 있어요.", color = TteumMuted, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("장소를 더 불러오지 못했어요", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(TteumSpacing.xs))
                        Text("검색어와 필터를 유지한 채 다시 시도해 주세요.", color = TteumMuted, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(TteumSpacing.md))
                        OutlinedButton(
                            onClick = onRetryLoadMore,
                            shape = TteumShapes.control,
                            contentPadding = TteumInsets.button,
                            modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                        ) {
                            Text("다시 시도", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else if (catalogState == CatalogUiState.EMPTY) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(TteumSpacing.screenHorizontal),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        when {
                            noSavedPlaces -> "아직 저장한 장소가 없어요"
                            hasActiveSearchOrFilter -> "조건에 맞는 장소가 없어요"
                            else -> "등록된 장소가 아직 없어요"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(TteumSpacing.xs))
                    Text(
                        when {
                            noSavedPlaces -> "마음에 드는 장소의 하트를 눌러 저장해 보세요."
                            hasActiveSearchOrFilter -> "검색어나 필터를 초기화하고 다른 장소를 둘러보세요."
                            else -> "다른 지역을 선택하면 새로운 장소를 볼 수 있어요."
                        },
                        color = TteumMuted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (hasActiveSearchOrFilter) {
                        Spacer(Modifier.height(TteumSpacing.md))
                        OutlinedButton(
                            shape = TteumShapes.control,
                            contentPadding = TteumInsets.button,
                            onClick = {
                                query = ""
                                selectedCategory = null
                                savedOnly = false
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                            },
                            modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                        ) {
                            Text(if (noSavedPlaces) "전체 장소 보기" else "검색·필터 초기화", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = cardMinWidth),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = TteumSpacing.screenHorizontal,
                        end = TteumSpacing.screenHorizontal,
                        bottom = TteumSpacing.sectionGap,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(TteumSpacing.itemGap),
                    verticalArrangement = Arrangement.spacedBy(TteumSpacing.itemGap),
                ) {
                    gridItems(visiblePlaces, key = { it.place.id }) { place ->
                        SavedPlaceCard(
                            place = place.place,
                            isSaved = place.savedEntry != null,
                            onClick = { selectedPlaceId = place.place.id },
                            onToggleSave = {
                                val removedEntry = place.savedEntry
                                onToggleSave(place.place)
                                if (removedEntry != null) {
                                    coroutineScope.launch {
                                        if (
                                            snackbarHostState.showSnackbar(
                                                message = "저장을 해제했어요.",
                                                actionLabel = "되돌리기",
                                            ) == SnackbarResult.ActionPerformed
                                        ) {
                                            onRestore(removedEntry)
                                        }
                                    }
                                }
                            },
                        )
                    }
                    if (!savedOnly) {
                        item(key = "catalog-continuation", span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = TteumSpacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(catalogContinuationLabel(hasMore, isLoadingMore, loadMoreFailed), color = TteumMuted, style = MaterialTheme.typography.bodySmall)
                                if (isLoadingMore) {
                                    CircularProgressIndicator(Modifier.padding(TteumSpacing.itemGap).size(24.dp))
                                } else if (hasMore && !loadMoreFailed) {
                                    TextButton(onClick = onLoadMore, modifier = Modifier.heightIn(min = TteumSizing.touchTarget), contentPadding = TteumInsets.button, shape = TteumShapes.control) {
                                        Text("장소 더 보기", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                        if (loadMoreFailed) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = TteumSpacing.itemGap),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "장소를 더 불러오지 못했어요.",
                                        color = TteumMuted,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Spacer(Modifier.height(TteumSpacing.xs))
                                    OutlinedButton(
                                        shape = TteumShapes.control,
                                        contentPadding = TteumInsets.button,
                                        onClick = onRetryLoadMore,
                                        modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                                    ) {
                                        Text("다시 시도", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedPlacesLoadingGrid(cardMinWidth: androidx.compose.ui.unit.Dp) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = cardMinWidth),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = TteumSpacing.screenHorizontal, end = TteumSpacing.screenHorizontal, bottom = TteumSpacing.sectionGap),
        horizontalArrangement = Arrangement.spacedBy(TteumSpacing.itemGap),
        verticalArrangement = Arrangement.spacedBy(TteumSpacing.itemGap),
        userScrollEnabled = false,
    ) {
        items(6) { index ->
            Surface(
                color = Color.White,
                shape = TteumShapes.card,
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(128.dp)
                            .background(
                                if (index % 2 == 0) Color(0xFFF0E8EA) else Color(0xFFE9EBEF),
                            ),
                    )
                    Column(
                        modifier = Modifier.padding(TteumInsets.card),
                        verticalArrangement = Arrangement.spacedBy(TteumSpacing.inlineGap),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.72f).height(18.dp),
                            color = Color(0xFFE9EBEF),
                            shape = TteumShapes.badge,
                        ) {}
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.45f).height(13.dp),
                            color = Color(0xFFF0F1F3),
                            shape = TteumShapes.badge,
                        ) {}
                    }
                }
            }
        }
    }
}

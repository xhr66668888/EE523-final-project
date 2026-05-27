package com.aeroscanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {
    var records by mutableStateOf<List<IndexedValue<CostRecord>>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var selectionMode by mutableStateOf(false)
    var selectedIndexes by mutableStateOf<Set<Int>>(emptySet())
    var expandedIndex by mutableStateOf<Int?>(null)
    private var appContext: android.content.Context? = null

    fun load(context: android.content.Context) {
        appContext = context
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                HistoryStore.init(context)
                records = HistoryStore.rankedEntries()
            } catch (e: Exception) {
                error = e.message ?: "Failed to load history."
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleSelection(index: Int) {
        selectedIndexes = if (index in selectedIndexes) {
            selectedIndexes - index
        } else {
            selectedIndexes + index
        }
    }

    fun deleteSelected() {
        val ctx = appContext ?: return
        viewModelScope.launch {
            try {
                val originalIndexes = selectedIndexes.map { records[it].index }.toSet()
                HistoryStore.delete(originalIndexes)
                selectedIndexes = emptySet()
                selectionMode = false
                HistoryStore.init(ctx)
                records = HistoryStore.rankedEntries()
            } catch (e: Exception) {
                error = e.message ?: "Failed to delete records."
            }
        }
    }

    fun toggleExpand(index: Int) {
        expandedIndex = if (expandedIndex == index) null else index
    }
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.load(context)
    }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.base)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Saved Trips",
                style = AeroscannerTypography.displayMedium,
                color = AeroscannerColors.Ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (viewModel.selectionMode) {
                    TextButton(onClick = {
                        viewModel.selectionMode = false
                        viewModel.selectedIndexes = emptySet()
                    }) {
                        Text("Cancel", color = AeroscannerColors.Muted)
                    }
                    val deleteColor = if (viewModel.selectedIndexes.isNotEmpty())
                        AeroscannerColors.ErrorText else AeroscannerColors.MutedSoft
                    TextButton(
                        onClick = { viewModel.deleteSelected() },
                        enabled = viewModel.selectedIndexes.isNotEmpty(),
                    ) {
                        Text(
                            "Delete (${viewModel.selectedIndexes.size})",
                            color = deleteColor,
                        )
                    }
                } else {
                    IconButton(onClick = {
                        viewModel.selectionMode = true
                    }) {
                        Icon(Icons.Default.Delete, "Delete", tint = AeroscannerColors.Muted)
                    }
                    IconButton(onClick = { viewModel.load(context) }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = AeroscannerColors.Muted)
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // Loading
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = AeroscannerColors.Primary)
            }
            return@Column
        }

        // Error
        AnimatedVisibility(visible = viewModel.error != null) {
            Card(
                shape = RoundedCornerShape(Radius.sm),
                colors = CardDefaults.cardColors(
                    containerColor = AeroscannerColors.Primary.copy(alpha = 0.08f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = AeroscannerColors.ErrorText,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(viewModel.error ?: "", style = AeroscannerTypography.bodyMedium, color = AeroscannerColors.ErrorText)
                        TextButton(onClick = { viewModel.load(context) }) {
                            Text("Retry", color = AeroscannerColors.Primary)
                        }
                    }
                }
            }
        }

        // Empty state
        if (!viewModel.isLoading && viewModel.records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = AeroscannerColors.MutedSoft,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(
                        "No saved trip analyses",
                        style = AeroscannerTypography.bodyLarge,
                        color = AeroscannerColors.Muted,
                    )
                    Text(
                        "Analyze a route on the Planner tab to save it here",
                        style = AeroscannerTypography.bodySmall,
                        color = AeroscannerColors.MutedSoft,
                    )
                }
            }
            return@Column
        }

        // Ranked list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            itemsIndexed(viewModel.records) { displayIndex, indexedRecord ->
                val record = indexedRecord.value
                val isExpanded = viewModel.expandedIndex == displayIndex
                val isSelected = displayIndex in viewModel.selectedIndexes

                HistoryCard(
                    rank = displayIndex + 1,
                    record = record,
                    isExpanded = isExpanded,
                    isSelectionMode = viewModel.selectionMode,
                    isSelected = isSelected,
                    onToggleExpand = { viewModel.toggleExpand(displayIndex) },
                    onToggleSelect = { viewModel.toggleSelection(displayIndex) },
                    onLongPress = {
                        viewModel.selectionMode = true
                        viewModel.toggleSelection(displayIndex)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    rank: Int,
    record: CostRecord,
    isExpanded: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
) {
    val level = try {
        CostTools.PriceLevel.valueOf(record.priceLevel)
    } catch (_: Exception) {
        CostTools.PriceLevel.MID
    }
    val tripCostRmb = if (record.totalTripCostRmb > 0) record.totalTripCostRmb else record.ticketPriceRmb

    Card(
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = AeroscannerColors.Canvas),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isSelected) AeroscannerColors.Primary else AeroscannerColors.Hairline
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect()
                    else onToggleExpand()
                },
                onLongClick = onLongPress,
            ),
    ) {
        Column(modifier = Modifier.padding(Spacing.base)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AeroscannerColors.Primary,
                        ),
                    )
                }

                // Rank number
                Text(
                    text = "#$rank",
                    style = AeroscannerTypography.titleMedium,
                    color = AeroscannerColors.Ink,
                )

                // Route
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.route,
                        style = AeroscannerTypography.titleMedium,
                        color = AeroscannerColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "¥${String.format("%.0f", tripCostRmb)} CNY · ${String.format("%.0f", record.totalDistanceKm)} km · ${record.source}",
                        style = AeroscannerTypography.bodySmall,
                        color = AeroscannerColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Cost/km
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "¥${String.format("%.2f", record.costPerKmRmb)}/km",
                        style = AeroscannerTypography.displayMedium.copy(fontSize = 20.sp),
                        color = CostTools.priceLevelColor(level),
                    )
                    Row(
                        modifier = Modifier
                            .background(
                                color = CostTools.priceLevelColor(level).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(Radius.full),
                            )
                            .padding(horizontal = Spacing.sm, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = CostTools.priceLevelColor(level),
                                    shape = RoundedCornerShape(Radius.full),
                                )
                        )
                        Text(
                            text = record.priceLevel,
                            style = AeroscannerTypography.labelSmall,
                            color = CostTools.priceLevelColor(level),
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = AeroscannerColors.Muted,
                    modifier = Modifier.size(24.dp),
                )
            }

            // Expanded details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    HorizontalDivider(color = AeroscannerColors.HairlineSoft)

                    if (record.segments.isNotEmpty()) {
                        Text("Segments", style = AeroscannerTypography.labelMedium, color = AeroscannerColors.Ink)
                        record.segments.forEach { seg ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${seg.originCode} → ${seg.destinationCode}",
                                    style = AeroscannerTypography.bodyMedium,
                                    color = AeroscannerColors.Ink,
                                )
                                Text(
                                    "${String.format("%.0f", seg.distanceKm)} km",
                                    style = AeroscannerTypography.bodySmall,
                                    color = AeroscannerColors.Muted,
                                )
                            }
                            Text(
                                "${seg.originCity} → ${seg.destinationCity}",
                                style = AeroscannerTypography.bodySmall,
                                color = AeroscannerColors.Muted,
                            )
                        }
                    }

                    if (record.surfaceTransfers.isNotEmpty()) {
                        Text(
                            "Surface Transfers",
                            style = AeroscannerTypography.labelMedium,
                            color = AeroscannerColors.Muted,
                        )
                        record.surfaceTransfers.forEach { transfer ->
                            Text(
                                "${transfer.originCity} → ${transfer.destinationCity} (${String.format("%.0f", transfer.distanceKm)} km ground)",
                                style = AeroscannerTypography.bodySmall,
                                color = AeroscannerColors.MutedSoft,
                            )
                        }
                    }

                    Text(
                        "Trip signals",
                        style = AeroscannerTypography.labelMedium,
                        color = AeroscannerColors.Ink,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "CO2e estimate",
                            style = AeroscannerTypography.bodySmall,
                            color = AeroscannerColors.Muted,
                        )
                        Text(
                            "${String.format("%.0f", record.carbonKg)} kg",
                            style = AeroscannerTypography.bodySmall,
                            color = AeroscannerColors.Muted,
                        )
                    }
                    if (record.baggageWeightKg != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Baggage fee",
                                style = AeroscannerTypography.bodySmall,
                                color = AeroscannerColors.Muted,
                            )
                            Text(
                                "¥${String.format("%.0f", record.baggageOverageFeeRmb ?: 0.0)} CNY",
                                style = AeroscannerTypography.bodySmall,
                                color = AeroscannerColors.Muted,
                            )
                        }
                    }

                    // Meta
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Source: ${record.source} · Input ${record.currency ?: "CNY"}",
                            style = AeroscannerTypography.bodySmall,
                            color = AeroscannerColors.MutedSoft,
                        )
                        Text(
                            record.createdAt.take(19).replace("T", " "),
                            style = AeroscannerTypography.bodySmall,
                            color = AeroscannerColors.MutedSoft,
                        )
                    }
                }
            }
        }
    }
}

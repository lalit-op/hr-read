package com.example.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.renderer.XlsxRendererContract
import com.example.renderer.xlsx.CellData
import com.example.renderer.xlsx.CellHorizontalAlignment
import com.example.renderer.xlsx.Worksheet
import com.example.renderer.xlsx.XlsxParser
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Native, high-performance virtualized spreadsheet viewer for Microsoft Excel (.xlsx, .xls) workbooks.
 *
 * Adheres strictly to the architectural pipeline:
 * XLSX -> XlsxRenderer -> Workbook -> Worksheet -> virtualized grid -> Android UI
 *
 * Implements true 2D virtualization:
 * - Vertical rows are lazily recycled via [LazyColumn]
 * - Horizontal columns are windowed so only visible/near-visible cells are composed
 * - Never composes thousands of Text nodes simultaneously
 * - Sticky row headers (1, 2, 3...) and sticky column headers (A, B, C...)
 * - Sheet selector tabs at the bottom (Sheet1 | Sheet2 | Sheet3)
 * - Formula / Active Cell Inspector bar
 */
@Composable
fun SpreadsheetViewer(
    renderer: XlsxRendererContract,
    currentSheetIndex: Int,
    onSheetSelected: (Int) -> Unit,
    searchQuery: String? = null,
    selectedCellRef: String? = null,
    zoomController: ZoomController? = null,
    onToggleControls: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetNames = remember(renderer) { renderer.getSheetNames() }
    var activeSheet by remember { mutableStateOf<Worksheet?>(null) }
    var isLoadingSheet by remember { mutableStateOf(false) }

    // Selected cell state for formula bar inspection
    var selectedCellRow by remember { mutableIntStateOf(0) }
    var selectedCellCol by remember { mutableIntStateOf(0) }

    val zoomScale = zoomController?.scale ?: 1.0f

    // Load active worksheet on sheet change without blocking UI
    LaunchedEffect(currentSheetIndex, renderer) {
        isLoadingSheet = true
        activeSheet = renderer.selectSheet(currentSheetIndex)
        isLoadingSheet = false
    }

    // React to external selectedCellRef navigation from search
    LaunchedEffect(selectedCellRef) {
        if (!selectedCellRef.isNullOrBlank()) {
            val colLetters = selectedCellRef.filter { it.isLetter() }
            val rowDigits = selectedCellRef.filter { it.isDigit() }
            if (colLetters.isNotEmpty() && rowDigits.isNotEmpty()) {
                val col = XlsxParser.colNameToIndex(colLetters)
                val row = (rowDigits.toIntOrNull() ?: 1) - 1
                if (row >= 0 && col >= 0) {
                    selectedCellRow = row
                    selectedCellCol = col
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Top Formula / Active Cell Inspector Bar
        val selectedCell = activeSheet?.getCell(selectedCellRow, selectedCellCol)
        val cellRef = remember(selectedCellRow, selectedCellCol) {
            "${XlsxParser.indexToColName(selectedCellCol)}${selectedCellRow + 1}"
        }

        CellInspectorBar(
            cellReference = cellRef,
            cellData = selectedCell,
            searchQuery = searchQuery,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // 2. Main Virtualized Spreadsheet Grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val sheet = activeSheet
            if (sheet != null) {
                VirtualizedSpreadsheetGrid(
                    worksheet = sheet,
                    selectedRow = selectedCellRow,
                    selectedCol = selectedCellCol,
                    searchQuery = searchQuery,
                    zoomScale = zoomScale,
                    onCellClicked = { r, c ->
                        selectedCellRow = r
                        selectedCellCol = c
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Loading overlay for fast worksheet switching
            if (isLoadingSheet) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = Color(0xFF1B5E20)
                        )
                        Text(
                            text = "Loading worksheet...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // 3. Spreadsheet Navigation & Zoom Control Bar
        SpreadsheetControlBar(
            currentSheet = currentSheetIndex,
            totalSheets = sheetNames.size,
            onPreviousSheet = {
                if (currentSheetIndex > 0) onSheetSelected(currentSheetIndex - 1)
            },
            onNextSheet = {
                if (currentSheetIndex < sheetNames.size - 1) onSheetSelected(currentSheetIndex + 1)
            },
            zoomScale = zoomScale,
            onZoomIn = { zoomController?.zoomIn() },
            onZoomOut = { zoomController?.zoomOut() },
            onResetZoom = { zoomController?.reset() },
            selectedCellRef = cellRef,
            onPreviousRow = {
                if (selectedCellRow > 0) selectedCellRow--
            },
            onNextRow = {
                val maxRow = (activeSheet?.rowCount ?: 40) - 1
                if (selectedCellRow < maxRow) selectedCellRow++
            },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // 4. Bottom Sheet Selector Bar (Sheet1 | Sheet2 | Sheet3)
        SheetSelectorBar(
            sheetNames = sheetNames,
            selectedIndex = currentSheetIndex,
            onSelectSheet = { idx ->
                if (idx != currentSheetIndex) {
                    onSheetSelected(idx)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sheet_selector_bar")
        )
    }
}

/**
 * Formula / Active Cell bar at the top of the spreadsheet.
 */
@Composable
private fun CellInspectorBar(
    cellReference: String,
    cellData: CellData?,
    searchQuery: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Coordinate badge (e.g. "B4")
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC8E6C9)),
                modifier = Modifier.width(64.dp)
            ) {
                Text(
                    text = cellReference,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF1B5E20),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Function icon (fx)
            Icon(
                imageVector = Icons.Filled.Functions,
                contentDescription = "Formula or Content",
                tint = Color(0xFF5F6368),
                modifier = Modifier.size(16.dp)
            )

            // Cell formula or formatted value
            val displayText = cellData?.formula?.let { "=$it" } ?: (cellData?.formattedValue ?: "")
            Text(
                text = displayText.ifEmpty { "(empty cell)" },
                style = MaterialTheme.typography.bodySmall,
                color = if (displayText.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Search query indicator if active
            if (!searchQuery.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = searchQuery,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * High-performance virtualized grid layout.
 *
 * Implements 2D virtualization:
 * - Rows are rendered via [LazyColumn]
 * - Columns inside each row are windowed based on horizontal scroll offset
 * - Synchronized sticky column headers (A, B, C...)
 * - Sticky row headers (1, 2, 3...)
 */
@Composable
private fun VirtualizedSpreadsheetGrid(
    worksheet: Worksheet,
    selectedRow: Int,
    selectedCol: Int,
    searchQuery: String?,
    zoomScale: Float = 1.0f,
    onCellClicked: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val rowHeaderWidth = (46.dp * zoomScale).coerceAtLeast(32.dp)
    val colHeaderHeight = (28.dp * zoomScale).coerceAtLeast(20.dp)

    val totalRows = max(worksheet.rowCount, 40)
    val totalCols = max(worksheet.columnCount, 12)

    // Precompute column widths in Dp scaled by zoomScale
    val colWidthsDp = remember(worksheet, totalCols, zoomScale) {
        List(totalCols) { col -> worksheet.getColumnWidthDp(col) * zoomScale }
    }

    val colWidthsPx = remember(colWidthsDp, density) {
        colWidthsDp.map { with(density) { it.toPx() } }
    }

    val totalGridWidthPx = remember(colWidthsPx) {
        colWidthsPx.sum()
    }

    val colOffsetsPx = remember(colWidthsPx) {
        val offsets = FloatArray(colWidthsPx.size + 1)
        var acc = 0f
        for (i in colWidthsPx.indices) {
            offsets[i] = acc
            acc += colWidthsPx[i]
        }
        offsets[colWidthsPx.size] = acc
        offsets
    }

    val horizontalScrollState = rememberScrollState()
    val lazyListState = rememberLazyListState()

    LaunchedEffect(selectedRow, selectedCol) {
        if (selectedRow >= 0 && selectedRow < totalRows) {
            lazyListState.animateScrollToItem(selectedRow)
        }
        if (selectedCol >= 0 && selectedCol < colOffsetsPx.size) {
            val targetX = colOffsetsPx[selectedCol].toInt()
            horizontalScrollState.animateScrollTo(targetX)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val viewportWidthPx = with(density) { maxWidth.toPx() } - with(density) { rowHeaderWidth.toPx() }

        // Compute visible column window based on current horizontal scroll
        val visibleColRange by remember(horizontalScrollState.value, viewportWidthPx, colOffsetsPx) {
            derivedStateOf {
                val scrollX = horizontalScrollState.value.toFloat()
                val left = max(0f, scrollX - 250f)
                val right = scrollX + viewportWidthPx + 250f

                var startCol = 0
                for (c in 0 until totalCols) {
                    if (colOffsetsPx[c + 1] >= left) {
                        startCol = c
                        break
                    }
                }

                var endCol = totalCols - 1
                for (c in startCol until totalCols) {
                    if (colOffsetsPx[c] > right) {
                        endCol = c
                        break
                    }
                }

                Pair(startCol, min(totalCols - 1, endCol))
            }
        }

        val (startCol, endCol) = visibleColRange
        val leadOffsetDp = with(density) { colOffsetsPx[startCol].toDp() }
        val tailOffsetDp = with(density) {
            max(0f, totalGridWidthPx - colOffsetsPx[endCol + 1]).toDp()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // A. Top Sticky Header Row (Corner box + Column letters A, B, C...)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(colHeaderHeight)
                    .background(Color(0xFFF1F3F4))
            ) {
                // Top-left junction corner
                Box(
                    modifier = Modifier
                        .width(rowHeaderWidth)
                        .fillMaxHeight()
                        .background(Color(0xFFE8EAED))
                        .border(1.dp, Color(0xFFD0D7DE)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "◢",
                        fontSize = 9.sp,
                        color = Color(0xFF80868B)
                    )
                }

                // Horizontally scrolled column headers (A, B, C...)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    if (leadOffsetDp > 0.dp) {
                        Spacer(modifier = Modifier.width(leadOffsetDp))
                    }

                    for (c in startCol..endCol) {
                        val colName = XlsxParser.indexToColName(c)
                        val isColSelected = (c == selectedCol)
                        val colW = colWidthsDp[c]

                        Box(
                            modifier = Modifier
                                .width(colW)
                                .fillMaxHeight()
                                .background(if (isColSelected) Color(0xFFE8F5E9) else Color(0xFFF1F3F4))
                                .border(1.dp, if (isColSelected) Color(0xFF2E7D32) else Color(0xFFD0D7DE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = colName,
                                fontSize = 11.sp,
                                fontWeight = if (isColSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isColSelected) Color(0xFF1B5E20) else Color(0xFF5F6368),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (tailOffsetDp > 0.dp) {
                        Spacer(modifier = Modifier.width(tailOffsetDp))
                    }
                }
            }

            // B. Main Data Rows Area (LazyColumn for vertical recycling)
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(totalRows) { r ->
                    val rowData = worksheet.getRow(r)
                    val rowHeightDp = worksheet.getRowHeightDp(r)
                    val isRowSelected = (r == selectedRow)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeightDp)
                    ) {
                        // Sticky Row Header (1, 2, 3...)
                        Box(
                            modifier = Modifier
                                .width(rowHeaderWidth)
                                .fillMaxHeight()
                                .background(if (isRowSelected) Color(0xFFE8F5E9) else Color(0xFFF1F3F4))
                                .border(1.dp, if (isRowSelected) Color(0xFF2E7D32) else Color(0xFFD0D7DE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (r + 1).toString(),
                                fontSize = (11f * zoomScale).coerceIn(8f, 18f).sp,
                                fontWeight = if (isRowSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isRowSelected) Color(0xFF1B5E20) else Color(0xFF5F6368),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Horizontally scrolled cells in this row (windowed!)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            if (leadOffsetDp > 0.dp) {
                                Spacer(modifier = Modifier.width(leadOffsetDp))
                            }

                            for (c in startCol..endCol) {
                                val cell = rowData?.cells?.get(c)
                                val colW = colWidthsDp[c]
                                val isCellSelected = (r == selectedRow && c == selectedCol)

                                val isSearchHit = remember(cell?.formattedValue, searchQuery) {
                                    if (!searchQuery.isNullOrBlank() && cell != null) {
                                        cell.formattedValue.contains(searchQuery, ignoreCase = true)
                                    } else false
                                }

                                VirtualizedCell(
                                    cell = cell,
                                    width = colW,
                                    height = rowHeightDp,
                                    zoomScale = zoomScale,
                                    isSelected = isCellSelected,
                                    isSearchHit = isSearchHit,
                                    onClick = { onCellClicked(r, c) }
                                )
                            }

                            if (tailOffsetDp > 0.dp) {
                                Spacer(modifier = Modifier.width(tailOffsetDp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual spreadsheet cell representation.
 */
@Composable
private fun VirtualizedCell(
    cell: CellData?,
    width: Dp,
    height: Dp,
    zoomScale: Float = 1.0f,
    isSelected: Boolean,
    isSearchHit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = cell?.style
    val customBg = style?.composeBackgroundColor

    val backgroundColor = when {
        isSearchHit -> Color(0xFFFFF59D) // Yellow search highlight
        isSelected -> Color(0xFFE8F5E9)   // Green active cell accent
        customBg != null -> customBg
        else -> Color.White
    }

    val borderColor = when {
        isSelected -> Color(0xFF2E7D32)
        isSearchHit -> Color(0xFFF57F17)
        style != null && style.border.hasAny -> Color(style.border.color)
        else -> Color(0xFFE0E0E0)
    }

    val borderWidth = if (isSelected) 2.dp else 0.5.dp

    val textAlignment = when (style?.horizontalAlignment) {
        CellHorizontalAlignment.RIGHT -> Alignment.CenterEnd
        CellHorizontalAlignment.CENTER -> Alignment.Center
        CellHorizontalAlignment.LEFT -> Alignment.CenterStart
        else -> Alignment.CenterStart
    }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .background(backgroundColor)
            .border(borderWidth, borderColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = textAlignment
    ) {
        if (cell != null && cell.formattedValue.isNotEmpty()) {
            Text(
                text = cell.formattedValue,
                color = style?.composeTextColor ?: Color(0xFF1F2328),
                fontSize = ((style?.fontSizeSp ?: 11f) * zoomScale).coerceIn(8f, 28f).sp,
                fontWeight = style?.fontWeight ?: FontWeight.Normal,
                fontStyle = style?.fontStyle ?: androidx.compose.ui.text.font.FontStyle.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = when (style?.horizontalAlignment) {
                    CellHorizontalAlignment.RIGHT -> TextAlign.End
                    CellHorizontalAlignment.CENTER -> TextAlign.Center
                    else -> TextAlign.Start
                }
            )
        }
    }
}

/**
 * Spreadsheet navigation and zoom toolbar
 */
@Composable
private fun SpreadsheetControlBar(
    currentSheet: Int,
    totalSheets: Int,
    onPreviousSheet: () -> Unit,
    onNextSheet: () -> Unit,
    zoomScale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    selectedCellRef: String,
    onPreviousRow: () -> Unit,
    onNextRow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Sheet navigation & cell jumping
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPreviousSheet,
                    enabled = currentSheet > 0,
                    modifier = Modifier.size(36.dp).testTag("sheet_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Previous sheet",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = if (totalSheets > 0) "${currentSheet + 1}/$totalSheets" else "1/1",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                IconButton(
                    onClick = onNextSheet,
                    enabled = currentSheet < totalSheets - 1,
                    modifier = Modifier.size(36.dp).testTag("sheet_next_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Next sheet",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Row navigation arrows
                IconButton(
                    onClick = onPreviousRow,
                    modifier = Modifier.size(36.dp).testTag("sheet_prev_row")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Previous row",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onNextRow,
                    modifier = Modifier.size(36.dp).testTag("sheet_next_row")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Next row",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Right: Zoom controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onZoomOut,
                    modifier = Modifier.size(36.dp).testTag("viewer_zoom_out")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ZoomOut,
                        contentDescription = "Zoom out",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "${(zoomScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onResetZoom)
                        .padding(horizontal = 4.dp)
                )

                IconButton(
                    onClick = onZoomIn,
                    modifier = Modifier.size(36.dp).testTag("viewer_zoom_in")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ZoomIn,
                        contentDescription = "Zoom in",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onResetZoom,
                    modifier = Modifier.size(36.dp).testTag("viewer_zoom_reset")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ZoomOutMap,
                        contentDescription = "Reset zoom",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Sheet selector tab bar at the bottom: Sheet1 | Sheet2 | Sheet3
 */
@Composable
private fun SheetSelectorBar(
    sheetNames: List<String>,
    selectedIndex: Int,
    onSelectSheet: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.TableChart,
                contentDescription = "Spreadsheet Worksheets",
                tint = Color(0xFF1B5E20),
                modifier = Modifier
                    .padding(start = 8.dp, end = 12.dp)
                    .size(18.dp)
            )

            // Scrollable tabs: Sheet1 | Sheet2 | Sheet3 ...
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(sheetNames) { index, name ->
                    val isSelected = (index == selectedIndex)
                    SheetTabItem(
                        name = name,
                        isSelected = isSelected,
                        onClick = { onSelectSheet(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetTabItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) Color.White else Color.Transparent
    val contentColor = if (isSelected) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isSelected) Color(0xFF2E7D32) else Color(0xFFD0D7DE)

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = borderColor
        ),
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF2E7D32))
                )
            }

            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

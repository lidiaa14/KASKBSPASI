package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Transaction
import com.example.util.ReportExporter
import com.example.util.Translations
import java.text.SimpleDateFormat
import java.util.*

// Dynamic palette of vibrant and rich colors matching light and dark themes
val VibrantChartColors = listOf(
    Color(0xFF2ECC71), // Emerald Green
    Color(0xFFE74C3C), // Alizarin Red/Coral
    Color(0xFF3498DB), // Peter River Blue
    Color(0xFFF39C12), // Orange
    Color(0xFF9B59B6), // Amethyst Purple
    Color(0xFF1ABC9C), // Turquoise Green
    Color(0xFFE67E22), // Pumpkin Orange
    Color(0xFF2C3E50), // Midnight Blue
    Color(0xFFF1C40F), // Sun Flower Yellow
    Color(0xFF16A085)  // Green Tea
)

// Helper to determine stable color based on string hash
fun getCategoryColor(category: String, index: Int): Color {
    val hash = category.hashCode()
    val listIndex = (if (hash < 0) -hash else hash) % VibrantChartColors.size
    return VibrantChartColors[(listIndex + index) % VibrantChartColors.size]
}

data class CategoryData(
    val category: String,
    val amount: Double,
    val percentage: Float,
    val color: Color
)

@Composable
fun CategoryDonutChart(
    transactions: List<Transaction>,
    language: String,
    modifier: Modifier = Modifier
) {
    var selectedType by remember { mutableStateOf("EXPENSE") } // INCOME or EXPENSE
    
    val t = { key: String -> Translations.get(key, language) }
    
    val filteredTxs = transactions.filter { it.type == selectedType }
    val totalAmount = filteredTxs.sumOf { it.amount }
    
    // Group and sort by amount
    val categoryTotals = filteredTxs
        .groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    val categoryDataList = categoryTotals.mapIndexed { index, pair ->
        val pct = if (totalAmount > 0.0) (pair.second / totalAmount).toFloat() else 0f
        CategoryData(
            category = pair.first,
            amount = pair.second,
            percentage = pct,
            color = if (selectedType == "INCOME") Color(0xFF047857) else getCategoryColor(pair.first, index)
        )
    }

    // Trigger state for expansion animation
    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(transactions, selectedType) {
        animationTriggered = false
        // Let composition settle before starting animation
        kotlinx.coroutines.delay(50)
        animationTriggered = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (animationTriggered) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "donut_expansion"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with type picker tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (language == "en") "Category Proportion" else "Proporsi Kategori",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Toggle Button Group
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedType == "INCOME") MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { selectedType = "INCOME" }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (language == "en") "Income" else "Masuk",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedType == "INCOME") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedType == "EXPENSE") MaterialTheme.colorScheme.errorContainer else Color.Transparent)
                            .clickable { selectedType = "EXPENSE" }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (language == "en") "Expenses" else "Keluar",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedType == "EXPENSE") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (totalAmount == 0.0) {
                // Elegantly styled empty state inside of the chart
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (language == "en") "No records found for this period" else "Belum ada transaksi di periode ini",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Responsive adaptive layout supporting dynamic screen constraints
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Donut circular visualization container
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .testTag("donut_chart_container"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val ringWidth = 26.dp.toPx()
                            val outlineRadius = (size.minDimension - ringWidth) / 2f
                            val centerOffset = Offset(size.width / 2f, size.height / 2f)

                            var currentAngle = -90f // Start pointing to the top

                            categoryDataList.forEach { data ->
                                if (data.percentage > 0f) {
                                    val baseSweep = data.percentage * 360f
                                    
                                    // Subtract a small gap and multiply by progress for smooth loading
                                    val sweepGap = if (categoryDataList.size > 1) 5f else 0f
                                    val sweepAngle = (baseSweep - sweepGap).coerceAtLeast(1f) * animatedProgress

                                    drawArc(
                                        color = data.color,
                                        startAngle = currentAngle,
                                        sweepAngle = sweepAngle,
                                        useCenter = false,
                                        style = Stroke(width = ringWidth, cap = StrokeCap.Round),
                                        topLeft = Offset(centerOffset.x - outlineRadius, centerOffset.y - outlineRadius),
                                        size = Size(outlineRadius * 2, outlineRadius * 2)
                                    )
                                    currentAngle += baseSweep
                                }
                            }
                        }

                        // Center content overlay
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = if (selectedType == "INCOME") t("total_income") else t("total_expenses"),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = ReportExporter.formatIDR(totalAmount),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Legend Column on the right with custom structured items
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Take top 4 categories and aggregate the rest
                        val maxDisplay = 4
                        val displayList = categoryDataList.take(maxDisplay)
                        val remainingList = categoryDataList.drop(maxDisplay)

                        displayList.forEach { data ->
                            LegendRowItem(data = data)
                        }

                        if (remainingList.isNotEmpty()) {
                            val remainAmt = remainingList.sumOf { it.amount }
                            val remainPct = remainingList.sumOf { it.percentage.toDouble() }.toFloat()
                            LegendRowItem(
                                data = CategoryData(
                                    category = if (language == "en") "Others" else "Lainnya",
                                    amount = remainAmt,
                                    percentage = remainPct,
                                    color = Color.Gray
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LegendRowItem(data: CategoryData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(data.color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = data.category,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "${(data.percentage * 100).toInt()}%",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

// Data class representing Income vs Expense grouped by localized context item
data class BarChartGroup(
    val dateLabel: String,
    val income: Double,
    val expense: Double
)

@Composable
fun IncomeExpenseBarChart(
    transactions: List<Transaction>,
    language: String,
    modifier: Modifier = Modifier
) {
    // Determine the latest 6 days of work within the active list
    val sortedTxs = transactions.sortedBy { it.date }
    val sdf = SimpleDateFormat("dd MMM", Locale("id", "ID"))

    // Group transactions by formatted date string
    val groupedByDate = sortedTxs.groupBy { sdf.format(Date(it.date)) }
    
    // Pick unique sorted dates list, or take recent 6
    val currentPeriodLabels = groupedByDate.keys.toList().takeLast(6)

    val barGroupsList = currentPeriodLabels.map { label ->
        val txsForDate = groupedByDate[label] ?: emptyList()
        val incSum = txsForDate.filter { it.type == "INCOME" }.sumOf { it.amount }
        val expSum = txsForDate.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        BarChartGroup(dateLabel = label, income = incSum, expense = expSum)
    }

    // If less than 3 bar groups are present, try to fill mock default milestones for a pleasant mock chart layout
    val barGroups = if (barGroupsList.size >= 3) {
        barGroupsList
    } else {
        // Fallback or fill a placeholder layout to meet visual requirements gracefully on empty database state
        listOf(
            BarChartGroup("1", 0.0, 0.0),
            BarChartGroup("2", 0.0, 0.0),
            BarChartGroup("3", 0.0, 0.0),
            BarChartGroup("4", 0.0, 0.0),
            BarChartGroup("5", 0.0, 0.0)
        )
    }

    val maxAmount = barGroups.maxOfOrNull { maxOf(it.income, it.expense) }?.coerceAtLeast(10000.0) ?: 10000.0

    // Trigger state for bar expansion animations
    var barAnimTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(transactions) {
        barAnimTriggered = false
        kotlinx.coroutines.delay(80)
        barAnimTriggered = true
    }

    val barProgressMultiplier by animateFloatAsState(
        targetValue = if (barAnimTriggered) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bar_growth"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (language == "en") "Cash Flow Matrix" else "Matriks Alur Kas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Indicators Legend
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2ECC71))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == "en") "In" else "Masuk",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE74C3C))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == "en") "Out" else "Keluar",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pure material Canvas renderer for accurate dual-bars drawing and dotted lines background
            val gridLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            val barWidth = with(LocalDensity.current) { 12.dp.toPx() }
            val barSpacing = with(LocalDensity.current) { 4.dp.toPx() }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(vertical = 10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasW = size.width
                    val canvasH = size.height

                    val labelAreaHeight = 30f
                    val chartHeight = canvasH - labelAreaHeight

                    // 1. Draw thin scale background grid axes (4 vertical divisions)
                    val stepMultiplier = listOf(0.0f, 0.33f, 0.66f, 1.0f)
                    stepMultiplier.forEach { fraction ->
                        val yOffset = chartHeight * (1f - fraction)
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, yOffset),
                            end = Offset(canvasW, yOffset),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        )
                    }

                    // 2. Draw groupings and comparative columns
                    val groupCount = barGroups.size
                    val segmentWidth = canvasW / groupCount

                    barGroups.forEachIndexed { index, group ->
                        val centerOfSegment = (segmentWidth * index) + (segmentWidth / 2f)

                        // Calculate visual heights proportional to Max Balance Limit
                        val incHeight = (chartHeight * (group.income / maxAmount).toFloat()).coerceAtLeast(0f) * barProgressMultiplier
                        val expHeight = (chartHeight * (group.expense / maxAmount).toFloat()).coerceAtLeast(0f) * barProgressMultiplier

                        // Left coordinate offset calculations for dual alignment side by side
                        val leftIn = centerOfSegment - barWidth - (barSpacing / 2f)
                        val leftOut = centerOfSegment + (barSpacing / 2f)

                        // Draw Income Bar (Emerald green with rounded corners)
                        if (incHeight > 3f) {
                            drawRoundRect(
                                color = Color(0xFF2ECC71),
                                topLeft = Offset(leftIn, chartHeight - incHeight),
                                size = Size(barWidth, incHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        } else {
                            // Flat visual guide marker for zero income
                            drawRoundRect(
                                color = Color(0xFF2ECC71).copy(alpha = 0.3f),
                                topLeft = Offset(leftIn, chartHeight - 4f),
                                size = Size(barWidth, 4f),
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                            )
                        }

                        // Draw Expense Bar (Vibrant red/coral with rounded corners)
                        if (expHeight > 3f) {
                            drawRoundRect(
                                color = Color(0xFFE74C3C),
                                topLeft = Offset(leftOut, chartHeight - expHeight),
                                size = Size(barWidth, expHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        } else {
                            // Flat visual guide marker for zero expense
                            drawRoundRect(
                                color = Color(0xFFE74C3C).copy(alpha = 0.3f),
                                topLeft = Offset(leftOut, chartHeight - 4f),
                                size = Size(barWidth, 4f),
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                            )
                        }
                    }
                }

                // Add responsive aligned textual overlay items for X-axis labels to avoid drawing raw font paths on Canvas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    barGroups.forEach { group ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = group.dateLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}



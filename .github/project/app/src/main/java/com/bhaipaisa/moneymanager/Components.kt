package com.bhaipaisa.moneymanager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// ---- Formatting ----

private val INDIA = Locale("en", "IN")

/** Whole rupees drop the ".00", the way payment apps show amounts. */
internal fun money(paise: Long): String {
    val format = NumberFormat.getCurrencyInstance(INDIA)
    if (paise % 100 == 0L) format.maximumFractionDigits = 0
    return format.format(paise / 100.0)
}

/** Rounded to the rupee, for headline numbers and chart centres. */
internal fun moneyRounded(paise: Long): String = money((paise + if (paise >= 0) 50 else -50) / 100 * 100)

/** Short Indian units for chart labels: ₹950, ₹2.2K, ₹1.4L, ₹3.1Cr. */
internal fun compactMoney(paise: Long): String {
    val rupees = abs(paise) / 100.0
    val sign = if (paise < 0) "-" else ""
    return sign + when {
        rupees >= 1_00_00_000 -> "₹%.1fCr".format(Locale.ENGLISH, rupees / 1_00_00_000)
        rupees >= 1_00_000 -> "₹%.1fL".format(Locale.ENGLISH, rupees / 1_00_000)
        rupees >= 1_000 -> "₹%.1fK".format(Locale.ENGLISH, rupees / 1_000)
        else -> "₹%.0f".format(Locale.ENGLISH, rupees)
    }.replace(".0", "")
}

/** "+₹500" for money in; "−₹500" for money out. */
internal fun signedMoney(paise: Long): String = if (paise > 0) "+${money(paise)}" else "−${money(-paise)}"

internal fun monthLabel(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
internal fun monthName(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))
internal fun shortMonth(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))

internal fun shortDate(epochDay: Long, today: LocalDate = LocalDate.now()): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val pattern = if (date.year == today.year) "d MMM" else "d MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
}

internal fun dayLabel(epochDay: Long, today: LocalDate = LocalDate.now()): String = when (epochDay) {
    today.toEpochDay() -> "Today"
    today.toEpochDay() - 1 -> "Yesterday"
    else -> LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern(if (LocalDate.ofEpochDay(epochDay).year == today.year) "EEEE, d MMMM" else "d MMMM yyyy", Locale.ENGLISH))
}

internal fun latestMonthSpend(transactions: List<Transaction>): Long {
    val month = transactions.maxOfOrNull { it.dateEpochDay }?.let(::monthOf) ?: return 0
    return transactions.filter { it.amountPaise < 0 && it.kind == TransactionKind.EXPENSE && monthOf(it.dateEpochDay) == month }.sumOf { -it.amountPaise }
}

// ---- Icons ----

internal fun categoryIcon(category: String, kind: TransactionKind): ImageVector {
    val name = category.lowercase(Locale.US)
    return when {
        kind == TransactionKind.INCOME -> Icons.Outlined.Payments
        kind == TransactionKind.TRANSFER -> Icons.Outlined.SwapHoriz
        kind == TransactionKind.CARD_SETTLEMENT -> Icons.Outlined.CreditCard
        kind == TransactionKind.INVESTMENT || "invest" in name -> Icons.AutoMirrored.Outlined.TrendingUp
        kind == TransactionKind.REFUND -> Icons.Outlined.Replay
        kind == TransactionKind.DEBT_PAYMENT -> Icons.Outlined.RequestQuote
        kind == TransactionKind.REVIEW -> Icons.AutoMirrored.Outlined.HelpOutline
        "food" in name || "grocer" in name -> Icons.Outlined.Restaurant
        "shop" in name -> Icons.Outlined.ShoppingBag
        "travel" in name || "transport" in name -> Icons.Outlined.DirectionsCar
        "subscri" in name -> Icons.Outlined.Subscriptions
        "charge" in name || "fee" in name -> Icons.Outlined.AccountBalance
        else -> Icons.AutoMirrored.Outlined.ReceiptLong
    }
}

internal fun holdingIcon(holding: Holding): ImageVector = when {
    holding.liability -> Icons.Outlined.RequestQuote
    holding.type == "Gold" -> Icons.Outlined.Diamond
    holding.type == "Property" -> Icons.Outlined.Apartment
    holding.type in listOf("Mutual funds", "Stocks", "NPS") -> Icons.AutoMirrored.Outlined.TrendingUp
    else -> Icons.Outlined.Savings
}

/** A stable colour per name, for merchant initials. */
internal fun nameColor(name: String): Color = CategoryPalette[abs(name.uppercase(Locale.US).hashCode()) % CategoryPalette.size]

// ---- Containers ----

internal val CardShape = RoundedCornerShape(20.dp)

/** A white card with a hairline border: the base surface for every group of content. */
@Composable
internal fun Panel(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) = Column(
    modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape).padding(padding),
    content = content
)

/** Rows that belong together share one card, split by hairlines. */
@Composable
internal fun <T> ListGroup(items: List<T>, row: @Composable (item: T, shape: Shape) -> Unit) = Panel(padding = 0.dp) {
    items.forEachIndexed { index, item ->
        if (index > 0) HorizontalDivider(Modifier.padding(start = 66.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        row(item, RectangleShape)
    }
}

/** The navy gradient card for a screen's headline number. Text inside is white in both themes. */
@Composable
internal fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Box(
    modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(HeroBrush)
) {
    Canvas(Modifier.matchParentSize()) {
        drawCircle(Color.White.copy(alpha = 0.06f), radius = size.height * 0.75f, center = Offset(size.width * 0.95f, -size.height * 0.1f))
        drawCircle(Color.White.copy(alpha = 0.05f), radius = size.height * 0.45f, center = Offset(size.width * 0.82f, size.height * 1.05f))
    }
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

// ---- Building blocks ----

@Composable
internal fun ListRow(
    title: String,
    supporting: String?,
    shape: Shape,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    supportingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supportingContent != null) supportingContent()
            else if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium, color = supportingColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun IconBadge(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 38.dp
) = Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(container), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.52f))
}

/** A merchant's first letter on its own colour, standing in for a logo. */
@Composable
internal fun InitialBadge(name: String, size: Dp = 38.dp) {
    val color = nameColor(name)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull { it.isLetter() }?.uppercase() ?: "₹", color = color, style = MaterialTheme.typography.titleMedium)
    }
}

/** Fold-style uppercase category tag, e.g. FOOD & GROCERY. */
@Composable
internal fun CategoryTag(text: String, icon: ImageVector? = null, warn: Boolean = false) = Row(
    Modifier.clip(RoundedCornerShape(6.dp))
        .background(if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 6.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
) {
    val color = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
    Text(text.uppercase(Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
}

@Composable
internal fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) = Row(
    Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    if (action != null && onAction != null) Text(
        action,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
internal fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) = Column(
    modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp)
) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
    }
    Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, message: String, action: String? = null, onAction: (() -> Unit)? = null) = Panel(padding = 22.dp) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconBadge(icon, size = 48.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null && onAction != null) Text(
            action,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(8.dp)
        )
    }
}

@Composable
internal fun Footnote(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 4.dp)
)

@Composable
internal fun MonthSwitcher(month: YearMonth, monthsBack: Int, onChange: (Int) -> Unit) = Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(onClick = { onChange(monthsBack + 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier month") }
    Text(monthLabel(month), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
    IconButton(onClick = { onChange(monthsBack - 1) }, enabled = monthsBack > 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later month") }
}

@Composable
internal fun RoundedProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = MaterialTheme.colorScheme.surfaceContainerHigh) = LinearProgressIndicator(
    progress = { fraction.coerceIn(0f, 1f) },
    modifier = modifier.fillMaxWidth().height(6.dp),
    color = color,
    trackColor = track,
    strokeCap = StrokeCap.Round,
    gapSize = 3.dp,
    drawStopIndicator = {}
)

@Composable
internal fun AmountText(paise: Long, signed: Boolean = true) = Text(
    if (signed) signedMoney(paise) else money(paise),
    style = AmountStyle,
    color = if (signed && paise > 0) positiveColor else MaterialTheme.colorScheme.onSurface
)

@Composable
internal fun LegendRow(color: Color, label: String, value: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
}

// ---- Charts ----

/** A ring split by share. Segments get a small gap so neighbours stay distinct. */
@Composable
internal fun Donut(
    shares: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier,
    thickness: Dp = 16.dp,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    center: @Composable BoxScope.() -> Unit = {}
) = Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
        val stroke = thickness.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arc = Size(diameter, diameter)
        drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
        val visible = shares.filter { it.second > 0f }
        val total = visible.sumOf { it.second.toDouble() }.toFloat()
        if (total <= 0f) return@Canvas
        val gap = if (visible.size > 1) 3f else 0f
        var start = -90f
        visible.forEach { (color, value) ->
            val sweep = 360f * value / total
            drawArc(color, start + gap / 2, (sweep - gap).coerceAtLeast(0.6f), false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Butt))
            start += sweep
        }
    }
    center()
}

/** Progress as a ring, e.g. how much of a budget is used. */
@Composable
internal fun Ring(fraction: Float, color: Color, modifier: Modifier = Modifier, thickness: Dp = 10.dp, track: Color = MaterialTheme.colorScheme.surfaceContainerHigh, center: @Composable BoxScope.() -> Unit = {}) = Box(
    modifier.aspectRatio(1f),
    contentAlignment = Alignment.Center
) {
    Canvas(Modifier.matchParentSize()) {
        val stroke = thickness.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        drawArc(track, 0f, 360f, false, topLeft, Size(diameter, diameter), style = Stroke(stroke))
        drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round))
    }
    center()
}

/** Monthly totals as rounded bars; the selected month is highlighted and each bar is tappable. */
@Composable
internal fun MonthBars(values: List<Pair<YearMonth, Long>>, selected: YearMonth, onSelect: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    val max = values.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    Row(modifier.fillMaxWidth().height(148.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { (month, value) ->
            val isSelected = month == selected
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable { onSelect(month) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (value == 0L) "" else compactMoney(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.width(26.dp).fillMaxHeight((value.toFloat() / max).coerceAtLeast(0.03f))
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                }
                Text(shortMonth(month), style = MaterialTheme.typography.labelMedium, color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A month grid with recurring payments marked on their days, in the navy hero style. */
@Composable
internal fun PaymentsCalendar(month: YearMonth, entries: List<CalendarEntry>, today: LocalDate = LocalDate.now()) = HeroCard {
    Text(monthLabel(month), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
            Text(day, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    val byDay = entries.groupBy { it.day }
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells = List(leading) { 0 } + (1..month.lengthOfMonth()).toList()
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            (week + List(7 - week.size) { 0 }).forEach { day ->
                Box(Modifier.weight(1f).height(38.dp), contentAlignment = Alignment.Center) {
                    if (day > 0) {
                        val marks = byDay[day].orEmpty()
                        val isToday = month == YearMonth.from(today) && day == today.dayOfMonth
                        val mark = marks.firstOrNull()
                        val fill = when (mark?.status) {
                            CalendarStatus.PAID -> Color.White.copy(alpha = 0.22f)
                            CalendarStatus.UPCOMING -> Color.White
                            CalendarStatus.MISSED -> Color(0xFFE5636B)
                            null -> Color.Transparent
                        }
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(fill)
                                .then(if (isToday) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                mark?.title?.firstOrNull { it.isLetter() }?.uppercase() ?: day.toString(),
                                style = if (mark != null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                                color = if (mark?.status == CalendarStatus.UPCOMING) Color(0xFF23264D) else Color.White.copy(alpha = if (mark == null) 0.85f else 1f)
                            )
                        }
                        if (marks.size > 1) Box(Modifier.align(Alignment.BottomCenter).size(4.dp).clip(CircleShape).background(Color.White))
                    }
                }
            }
        }
    }
    val counts = CalendarStatus.entries.associateWith { status -> entries.count { it.status == status } }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(CalendarStatus.UPCOMING to "upcoming", CalendarStatus.MISSED to "missed", CalendarStatus.PAID to "paid").forEach { (status, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(when (status) {
                    CalendarStatus.UPCOMING -> Color.White
                    CalendarStatus.MISSED -> Color(0xFFE5636B)
                    CalendarStatus.PAID -> Color.White.copy(alpha = 0.35f)
                }))
                Text("${counts[status] ?: 0} $label", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

// ---- Domain rows ----

@Composable
internal fun TransactionRow(transaction: Transaction, shape: Shape, onClick: (Transaction) -> Unit, showDate: Boolean = true) {
    val needsReview = transaction.kind == TransactionKind.REVIEW
    val icon = categoryIcon(transaction.category, transaction.kind)
    ListRow(
        title = transaction.title,
        supporting = null,
        shape = shape,
        leading = {
            if (needsReview) IconBadge(icon, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            else IconBadge(icon)
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryTag(if (needsReview) "Needs a category" else transaction.category, warn = needsReview)
                if (showDate) Text(shortDate(transaction.dateEpochDay), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailing = { AmountText(transaction.amountPaise) },
        onClick = { onClick(transaction) }
    )
}

@Composable
internal fun BudgetRow(progress: BudgetProgress, shape: Shape, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp)
) {
    IconBadge(categoryIcon(progress.category, TransactionKind.EXPENSE))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(progress.category, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (progress.overLimit) "${money(-progress.remainingPaise)} over" else "${money(progress.remainingPaise)} left",
                style = MaterialTheme.typography.labelLarge,
                color = if (progress.overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        RoundedProgress(progress.fraction, if (progress.overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
        Text("${money(progress.spentPaise)} of ${money(progress.limitPaise)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun RecurringRow(payment: RecurringPayment, shape: Shape) = ListRow(
    title = payment.title,
    supporting = null,
    shape = shape,
    leading = { InitialBadge(payment.title) },
    supportingContent = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryTag(payment.cadence.label, warn = payment.missed)
            Text(
                if (payment.missed) "not seen since ${shortDate(payment.lastEpochDay)}" else "next ${shortDate(payment.nextExpectedEpochDay)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    },
    trailing = {
        Column(horizontalAlignment = Alignment.End) {
            Text(money(payment.latestPaise), style = AmountStyle)
            if (payment.priceIncreased) Text("was ${money(payment.previousPaise)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
)

/** A small card for horizontal carousels, like an app tile on the Play Store. */
@Composable
internal fun UpcomingCard(payment: RecurringPayment) = Panel(Modifier.width(150.dp), padding = 14.dp) {
    InitialBadge(payment.title, size = 40.dp)
    Text(payment.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
    Text(money(payment.latestPaise), style = MaterialTheme.typography.titleMedium)
    Text(
        if (payment.priceIncreased) "Up from ${money(payment.previousPaise)}" else "Due ${shortDate(payment.nextExpectedEpochDay)}",
        style = MaterialTheme.typography.bodySmall,
        color = if (payment.priceIncreased) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

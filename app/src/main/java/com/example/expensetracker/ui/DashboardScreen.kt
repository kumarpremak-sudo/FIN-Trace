package com.example.expensetracker.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.CategorySum
import com.example.expensetracker.data.CurrencySum
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.data.TransactionDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Timeframe { DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }

data class DashboardState(
    val spentByCurrency: List<CurrencySum> = emptyList(),
    val investedByCurrency: List<CurrencySum> = emptyList(),
    val categoryBreakdown: List<CategorySum> = emptyList(),
    val isLoading: Boolean = true
)

class DashboardViewModel(private val dao: TransactionDao) : ViewModel() {

    private val _timeframe = MutableStateFlow(Timeframe.MONTHLY)
    val timeframe: StateFlow<Timeframe> = _timeframe

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth

    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear

    private val _refreshTrigger = MutableStateFlow(0)

    private fun getRange(timeframe: Timeframe, month: Int, year: Int): Pair<Long, Long> {
        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance()

        // Common: Reset time components for the start
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)

        // Common: Set time to the very end of the day for the end
        endCal.set(Calendar.HOUR_OF_DAY, 23)
        endCal.set(Calendar.MINUTE, 59)
        endCal.set(Calendar.SECOND, 59)
        endCal.set(Calendar.MILLISECOND, 999)

        return when (timeframe) {
            Timeframe.DAILY -> {
                startCal.timeInMillis to endCal.timeInMillis
            }
            Timeframe.WEEKLY -> {
                // Set start to the first day of the week
                startCal.set(Calendar.DAY_OF_WEEK, startCal.firstDayOfWeek)
                // Set end to the last day of the week (Start + 6 days)
                endCal.timeInMillis = startCal.timeInMillis + (7L * 24 * 60 * 60 * 1000) - 1
                startCal.timeInMillis to endCal.timeInMillis
            }
            Timeframe.MONTHLY -> {
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                startCal.timeInMillis to endCal.timeInMillis
            }
            Timeframe.YEARLY -> {
                startCal.set(Calendar.DAY_OF_YEAR, 1)
                endCal.set(Calendar.MONTH, Calendar.DECEMBER)
                endCal.set(Calendar.DAY_OF_MONTH, 31)
                startCal.timeInMillis to endCal.timeInMillis
            }
            Timeframe.CUSTOM -> {
                val customStart = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val customEnd = (customStart.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                customStart.timeInMillis to customEnd.timeInMillis
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboardData: StateFlow<DashboardState> = combine(
        _timeframe, _selectedMonth, _selectedYear, _refreshTrigger
    ) { tf, m, y, _ ->
        getRange(tf, m, y)
    }.distinctUntilChanged()
    .flatMapLatest { range ->
        combine(
            dao.getTotalSpentByCurrency(range.first, range.second),
            dao.getTotalInvestedByCurrency(range.first, range.second),
            dao.getExpensesByCategory(range.first, range.second)
        ) { spent, invested, categories ->
            DashboardState(spent, invested, categories, isLoading = false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState(isLoading = true))

    val recentTransactions: StateFlow<List<Transaction>> = dao.getRecentTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTimeframe(tf: Timeframe) { _timeframe.value = tf }
    fun setMonth(m: Int) { _selectedMonth.value = m; _timeframe.value = Timeframe.CUSTOM }
    fun setYear(y: Int) { _selectedYear.value = y; _timeframe.value = Timeframe.CUSTOM }
    fun refresh() { _refreshTrigger.value += 1 }
}

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val timeframe by viewModel.timeframe.collectAsState()
    val month by viewModel.selectedMonth.collectAsState()
    val year by viewModel.selectedYear.collectAsState()
    val data by viewModel.dashboardData.collectAsState()
    val transactions by viewModel.recentTransactions.collectAsState()
    
    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FE))) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(Brush.verticalGradient(colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))))
                .padding(top = 24.dp, bottom = 40.dp, start = 24.dp, end = 24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Portfolio Performance", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (timeframe == Timeframe.CUSTOM) "${DateFormatSymbols().months[month]} $year" else timeframe.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(
                        onClick = { showDatePicker = !showDatePicker },
                        modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Insights, contentDescription = "Range", tint = Color.White)
                    }
                }

                if (showDatePicker) {
                    DatePickerView(month, year, onMonthSelected = viewModel::setMonth, onYearSelected = viewModel::setYear)
                }

                Spacer(modifier = Modifier.height(32.dp))
                WealthCard(data)
            }
        }

        val entries = remember { Timeframe.entries.take(4) }
        TabRow(
            selectedTabIndex = if (timeframe == Timeframe.CUSTOM) 0 else timeframe.ordinal,
            containerColor = Color.Transparent,
            contentColor = Color(0xFF6366F1),
            divider = {},
            indicator = { tabPositions ->
                val index = if (timeframe == Timeframe.CUSTOM) 0 else timeframe.ordinal
                if (index < tabPositions.size) {
                    Box(Modifier.tabIndicatorOffset(tabPositions[index]).height(3.dp).padding(horizontal = 24.dp).background(Color(0xFF6366F1), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                }
            }
        ) {
            entries.forEach { tf ->
                Tab(selected = timeframe == tf, onClick = { viewModel.setTimeframe(tf) }, text = { Text(tf.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 13.sp) })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (data.isLoading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape), color = Color(0xFF6366F1)) }
            }

            item(key = "trend_card") { TrendInsightCard(data) }

            if (data.categoryBreakdown.isNotEmpty()) {
                item(key = "spending_header") { Text("Spending Analysis", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF1E293B)) }
                val maxAmount = data.categoryBreakdown.maxOfOrNull { it.totalAmount } ?: 1.0
                items(data.categoryBreakdown.take(3), key = { it.category }) { cat ->
                    GlobalCategoryProgress(cat, maxAmount)
                }
            }

            item(key = "activity_header") {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent Insights", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF1E293B))
                    TextButton(onClick = { viewModel.refresh() }) { Text("Refresh Feed", color = Color(0xFF6366F1), fontWeight = FontWeight.Bold) }
                }
            }

            if (transactions.isEmpty()) {
                item(key = "no_transactions") { Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text("No records found for this period.", color = Color.Gray) } }
            } else {
                items(transactions, key = { it.id }) { tx -> GlobalTransactionItem(tx) }
            }
        }
    }
}

@Composable
fun WealthCard(data: DashboardState) {
    val allCurrencies = remember(data) { (data.spentByCurrency.map { it.currency } + data.investedByCurrency.map { it.currency }).distinct() }
    
    if (allCurrencies.isEmpty()) {
        SimpleWealthCard("0.00", "INR")
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            allCurrencies.forEach { curr ->
                val spent = remember(data, curr) { data.spentByCurrency.find { it.currency == curr }?.totalAmount ?: 0.0 }
                val invested = remember(data, curr) { data.investedByCurrency.find { it.currency == curr }?.totalAmount ?: 0.0 }
                WealthSummaryCard(spent, invested, curr)
            }
        }
    }
}

@Composable
fun WealthSummaryCard(spent: Double, invested: Double, currency: String) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = Modifier.width(280.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Estimated Cashflow ($currency)", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Text(text = formatCurrency(invested - spent, currency), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Total Outflow", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    Text(formatCurrency(spent, currency), color = Color(0xFFFDA4AF), fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Investment", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    Text(formatCurrency(invested, currency), color = Color(0xFF6EE7B7), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SimpleWealthCard(amount: String, currency: String) {
    Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Current Balance", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Text("$currency $amount", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun TrendInsightCard(data: DashboardState) {
    Surface(color = Color.White, shape = RoundedCornerShape(24.dp), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(Color(0xFFEEF2FF), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoGraph, contentDescription = null, tint = Color(0xFF6366F1))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Spending Velocity", color = Color.Gray, fontSize = 12.sp)
                Text(text = if (data.spentByCurrency.isEmpty()) "Optimal" else "Tracking Trends...", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
            }
            Spacer(modifier = Modifier.weight(1f))
            Canvas(modifier = Modifier.size(60.dp, 30.dp)) {
                val path = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width * 0.2f, size.height * 0.7f)
                    lineTo(size.width * 0.4f, size.height * 0.9f)
                    lineTo(size.width * 0.7f, size.height * 0.2f)
                    lineTo(size.width, size.height * 0.4f)
                }
                drawPath(path, color = Color(0xFF6366F1), style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@Composable
fun GlobalCategoryProgress(cat: CategorySum, max: Double) {
    val progress = remember(cat, max) { if (max > 0) (cat.totalAmount / max).toFloat() else 0f }
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(cat.category, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF334155))
            Text("₹${"%.0f".format(cat.totalAmount)}", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF1E293B))
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress }, 
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), 
            color = Color(0xFF6366F1), 
            trackColor = Color(0xFFE2E8F0)
        )
    }
}

@Composable
fun GlobalTransactionItem(tx: Transaction) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val dateString = remember(tx.timestamp) { dateFormat.format(Date(tx.timestamp)) }
    
    val typeColor = remember(tx.type) {
        when(tx.type) {
            "INVESTMENT" -> Color(0xFFDCFCE7) to Color(0xFF15803D)
            "DEBIT" -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
            else -> Color(0xFFF3E5F5) to Color(0xFF6366F1)
        }
    }

    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(), shadowElevation = 0.5.dp) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).background(typeColor.first, CircleShape), contentAlignment = Alignment.Center) {
                Icon(imageVector = when(tx.type) { "INVESTMENT" -> Icons.Default.TrendingUp; "DEBIT" -> Icons.Default.ShoppingCart; else -> Icons.Default.Payments }, contentDescription = null, modifier = Modifier.size(20.dp), tint = typeColor.second)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tx.merchant, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dateString, color = Color.Gray, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "${if (tx.type == "DEBIT") "-" else ""} ${formatCurrency(tx.amount, tx.currency)}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = if (tx.type == "DEBIT") Color(0xFF1E293B) else Color(0xFF15803D))
                Text(tx.category, color = Color.Gray.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

fun formatCurrency(amount: Double, currency: String): String {
    val symbol = when(currency) {
        "INR" -> "₹"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> "$currency "
    }
    return "$symbol${"%.2f".format(amount)}"
}

@Composable
fun DatePickerView(currentMonth: Int, currentYear: Int, onMonthSelected: (Int) -> Unit, onYearSelected: (Int) -> Unit) {
    val months = remember { DateFormatSymbols().months.take(12) }
    val years = remember { (2020..Calendar.getInstance().get(Calendar.YEAR)).toList() }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp)).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
           var expandedMonth by remember { mutableStateOf(false) }
           var expandedYear by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { expandedMonth = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))) { Text(months[currentMonth], color = Color.White) }
                DropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                    months.forEachIndexed { index, m -> DropdownMenuItem(text = { Text(m) }, onClick = { onMonthSelected(index); expandedMonth = false }) }
                }
            }
            Box {
                Button(onClick = { expandedYear = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))) { Text(currentYear.toString(), color = Color.White) }
                DropdownMenu(expanded = expandedYear, onDismissRequest = { expandedYear = false }) {
                    years.forEach { y -> DropdownMenuItem(text = { Text(y.toString()) }, onClick = { onYearSelected(y); expandedYear = false }) }
                }
            }
        }
    }
}

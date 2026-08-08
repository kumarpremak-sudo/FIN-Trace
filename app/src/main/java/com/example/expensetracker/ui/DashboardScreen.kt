package com.example.expensetracker.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.CategorySum
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
    val totalSpent: Double = 0.0,
    val totalInvested: Double = 0.0,
    val categoryBreakdown: List<CategorySum> = emptyList()
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
        val cal = Calendar.getInstance()
        val end = cal.timeInMillis
        
        return when (timeframe) {
            Timeframe.DAILY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to end
            }
            Timeframe.WEEKLY -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to end
            }
            Timeframe.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to end
            }
            Timeframe.YEARLY -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to end
            }
            Timeframe.CUSTOM -> {
                val startCal = Calendar.getInstance()
                startCal.set(Calendar.YEAR, year)
                startCal.set(Calendar.MONTH, month)
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                startCal.set(Calendar.HOUR_OF_DAY, 0); startCal.set(Calendar.MINUTE, 0); startCal.set(Calendar.SECOND, 0); startCal.set(Calendar.MILLISECOND, 0)
                
                val endCal = (startCal.clone() as Calendar)
                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                endCal.set(Calendar.HOUR_OF_DAY, 23); endCal.set(Calendar.MINUTE, 59); endCal.set(Calendar.SECOND, 59); endCal.set(Calendar.MILLISECOND, 999)
                
                startCal.timeInMillis to endCal.timeInMillis
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
            dao.getTotalSpent(range.first, range.second),
            dao.getTotalInvested(range.first, range.second),
            dao.getExpensesByCategory(range.first, range.second)
        ) { spent, invested, categories ->
            DashboardState(spent ?: 0.0, invested ?: 0.0, categories)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

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

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, Color(0xFF4338CA))
                    )
                )
                .padding(top = 24.dp, bottom = 32.dp, start = 20.dp, end = 20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Overview", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        Text(
                            text = if (timeframe == Timeframe.CUSTOM) 
                                "${DateFormatSymbols().months[month]} $year" 
                            else "Recent Activity",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { showDatePicker = !showDatePicker },
                        modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = "Pick Date", tint = Color.White)
                    }
                }
                
                if (showDatePicker) {
                    DatePickerView(month, year, onMonthSelected = viewModel::setMonth, onYearSelected = viewModel::setYear)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = "Expenses",
                        amount = data.totalSpent,
                        icon = Icons.Default.ArrowDownward,
                        color = Color(0xFFFFEBEE),
                        contentColor = Color(0xFFC62828),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Investments",
                        amount = data.totalInvested,
                        icon = Icons.Default.ArrowUpward,
                        color = Color(0xFFE8F5E9),
                        contentColor = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        val entries = Timeframe.entries.take(4)
        TabRow(
            selectedTabIndex = if (timeframe == Timeframe.CUSTOM) 0 else timeframe.ordinal,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {},
            indicator = { tabPositions ->
                val index = if (timeframe == Timeframe.CUSTOM) 0 else timeframe.ordinal
                if (index < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[index]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            entries.forEach { tf ->
                Tab(
                    selected = timeframe == tf,
                    onClick = { viewModel.setTimeframe(tf) },
                    text = { Text(tf.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (data.categoryBreakdown.isNotEmpty()) {
                item {
                    Text("Top Categories", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                val maxAmount = data.categoryBreakdown.firstOrNull()?.totalAmount ?: 1.0
                items(data.categoryBreakdown.take(3), key = { it.category }) { cat ->
                    CategoryProgress(cat, maxAmount)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Activity", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text("Refresh")
                    }
                }
            }

            if (transactions.isEmpty()) {
                item {
                    Text("No transactions found.", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    TransactionItem(tx)
                }
            }
        }
    }
}

@Composable
fun DatePickerView(currentMonth: Int, currentYear: Int, onMonthSelected: (Int) -> Unit, onYearSelected: (Int) -> Unit) {
    val months = DateFormatSymbols().months.take(12)
    val years = (2020..Calendar.getInstance().get(Calendar.YEAR)).toList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
           var expandedMonth by remember { mutableStateOf(false) }
           var expandedYear by remember { mutableStateOf(false) }

            Box {
                Button(
                    onClick = { expandedMonth = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) {
                    Text(months[currentMonth], color = Color.White)
                }
                DropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                    months.forEachIndexed { index, m ->
                        DropdownMenuItem(text = { Text(m) }, onClick = { onMonthSelected(index); expandedMonth = false })
                    }
                }
            }

            Box {
                Button(
                    onClick = { expandedYear = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) {
                    Text(currentYear.toString(), color = Color.White)
                }
                DropdownMenu(expanded = expandedYear, onDismissRequest = { expandedYear = false }) {
                    years.forEach { y ->
                        DropdownMenuItem(text = { Text(y.toString()) }, onClick = { onYearSelected(y); expandedYear = false })
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, amount: Double, icon: ImageVector, color: Color, contentColor: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = color,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = contentColor.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("₹${"%.0f".format(amount)}", color = contentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CategoryProgress(cat: CategorySum, max: Double) {
    val progress = if (max > 0) (cat.totalAmount / max).toFloat() else 0f
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(cat.category, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text("₹${"%.0f".format(cat.totalAmount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
fun TransactionItem(tx: Transaction) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val dateString = remember(tx.timestamp) { dateFormat.format(Date(tx.timestamp)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    when(tx.type) {
                        "INVESTMENT" -> Color(0xFFE8F5E9)
                        "DEBIT" -> Color(0xFFFFEBEE)
                        else -> Color(0xFFF3E5F5)
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when(tx.type) {
                    "INVESTMENT" -> Icons.Default.TrendingUp
                    "DEBIT" -> Icons.Default.ShoppingCart
                    else -> Icons.Default.AccountBalanceWallet
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when(tx.type) {
                    "INVESTMENT" -> Color(0xFF2E7D32)
                    "DEBIT" -> Color(0xFFC62828)
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.merchant, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tx.category, color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(2.dp).background(Color.LightGray, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text(dateString, color = Color.Gray, fontSize = 11.sp)
            }
        }
        Text(
            text = "${if (tx.type == "DEBIT") "-" else ""} ${formatCurrency(tx.amount, tx.currency)}",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            color = if (tx.type == "DEBIT") Color(0xFFC62828) else Color(0xFF2E7D32)
        )
    }
}

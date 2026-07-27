package com.example.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.MerchantMapping
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.data.TransactionDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReviewViewModel(private val dao: TransactionDao) : ViewModel() {

    val pendingTransactions: StateFlow<List<Transaction>> = dao.getUncategorizedTransactions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val availableCategories = listOf("Food", "Transport", "Shopping", "Groceries", "Bills", "Investment", "Health", "Personal")

    fun assignCategory(transaction: Transaction, category: String) {
        viewModelScope.launch {
            dao.updateTransactionCategory(transaction.id, category)
            transaction.merchant?.let { merchantName ->
                val rule = MerchantMapping(
                    merchant = merchantName.lowercase(),
                    category = category
                )
                dao.insertMerchantRule(rule)
            }
        }
    }
}

@Composable
fun ReviewScreen(viewModel: ReviewViewModel) {
    val pendingList by viewModel.pendingTransactions.collectAsState()
    val categories = viewModel.availableCategories

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Modern Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5)) // Indigo gradient
                    )
                )
                .padding(top = 24.dp, bottom = 32.dp, start = 20.dp, end = 20.dp)
        ) {
            Column {
                Text("Action Required", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Text(
                    text = "Triage (${pendingList.size})", 
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Assign categories to teach the app about your spending habits.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        if (pendingList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("All caught up! 🎉", color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(20.dp)
            ) {
                items(pendingList) { tx ->
                    UncategorizedItem(
                        transaction = tx,
                        categories = categories,
                        onCategorySelected = { selectedCategory ->
                            viewModel.assignCategory(tx, selectedCategory)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UncategorizedItem(
    transaction: Transaction,
    categories: List<String>,
    onCategorySelected: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier.size(32.dp).background(Color.LightGray.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QuestionMark, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = transaction.merchant ?: "Unknown Merchant", 
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "₹${"%.2f".format(transaction.amount)}", 
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = when(transaction.type) {
                        "DEBIT" -> Color(0xFFC62828)
                        "INVESTMENT" -> Color(0xFF2E7D32)
                        else -> Color(0xFF4CAF50)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp)
            ) {
                Text(
                    text = transaction.rawSms,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "SELECT CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = false,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category, fontSize = 11.sp) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = null
                    )
                }
            }
        }
    }
}

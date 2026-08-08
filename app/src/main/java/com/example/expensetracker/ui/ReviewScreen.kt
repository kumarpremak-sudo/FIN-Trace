package com.example.expensetracker.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableCategories = listOf("Food", "Transport", "Shopping", "Groceries", "Bills", "Investment", "Health", "Personal")

    fun assignCategoryAndName(transaction: Transaction, newName: String, category: String) {
        viewModelScope.launch {
            val rawKey = transaction.rawMerchant.ifBlank { transaction.merchant }.lowercase()
            dao.applyRuleAndRename(rawKey, newName, category)
        }
    }
}

@Composable
fun ReviewScreen(viewModel: ReviewViewModel) {
    val pendingList by viewModel.pendingTransactions.collectAsState()
    val categories = viewModel.availableCategories

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FE))) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(Brush.verticalGradient(colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5))))
                .padding(top = 24.dp, bottom = 40.dp, start = 24.dp, end = 24.dp)
        ) {
            Column {
                Text("Action Required", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = if (pendingList.isEmpty()) "Everything Sorted!" else "${pendingList.size} Pending Triage", 
                    color = Color.White, 
                    fontSize = 28.sp, 
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Teach the assistant how to name your merchants.", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }

        if (pendingList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(80.dp).background(Color(0xFFDCFCE7), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color(0xFF15803D))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("All transactions are categorized!", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("New spending will appear here automatically.", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(24.dp)
            ) {
                items(pendingList, key = { it.id }) { tx ->
                    GlobalTriageItem(tx, categories, onDone = viewModel::assignCategoryAndName)
                }
            }
        }
    }
}

@Composable
fun GlobalTriageItem(tx: Transaction, categories: List<String>, onDone: (Transaction, String, String) -> Unit) {
    var editedName by remember(tx.id) { mutableStateOf(tx.merchant) }
    var selectedCategory by remember(tx.id) { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).background(Color(0xFFF1F5F9), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF64748B))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Transaction Found", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        text = formatCurrency(tx.amount, tx.currency), 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.Black, 
                        color = Color(0xFF1E293B)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (selectedCategory.isNotEmpty() && editedName.isNotBlank()) {
                    IconButton(
                        onClick = { onDone(tx, editedName, selectedCategory) },
                        modifier = Modifier.size(44.dp).background(Color(0xFF6366F1), CircleShape)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text("Display Name") },
                placeholder = { Text("e.g. Starbucks Coffee") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            // Expandable SMS Context
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF8F9FE))
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tx.rawSms, 
                            fontSize = 12.sp, 
                            color = Color(0xFF64748B), 
                            maxLines = if (isExpanded) Int.MAX_VALUE else 2, 
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            lineHeight = 18.sp
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, 
                            contentDescription = null, 
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("ASSIGN CATEGORY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF6366F1),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

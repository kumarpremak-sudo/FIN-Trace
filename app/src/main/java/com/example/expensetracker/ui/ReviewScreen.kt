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
            dao.applyRuleAndRename(transaction.rawMerchant.lowercase(), newName, category)
        }
    }
}

@Composable
fun ReviewScreen(viewModel: ReviewViewModel) {
    val pendingList by viewModel.pendingTransactions.collectAsState()
    val categories = viewModel.availableCategories

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.verticalGradient(colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5))))
                .padding(top = 24.dp, bottom = 32.dp, start = 20.dp, end = 20.dp)
        ) {
            Column {
                Text("Action Required", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Text("Triage (${pendingList.size})", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Correct merchant names and assign categories to automate future tracking.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
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
                items(pendingList, key = { it.id }) { tx ->
                    TriageItem(tx, categories, onDone = viewModel::assignCategoryAndName)
                }
            }
        }
    }
}

@Composable
fun TriageItem(tx: Transaction, categories: List<String>, onDone: (Transaction, String, String) -> Unit) {
    var editedName by remember(tx.id) { mutableStateOf(tx.merchant) }
    var selectedCategory by remember(tx.id) { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "₹${"%.2f".format(tx.amount)}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (tx.type == "DEBIT") Color(0xFFC62828) else Color(0xFF2E7D32))
                Spacer(modifier = Modifier.weight(1f))
                if (selectedCategory.isNotEmpty() && editedName.isNotBlank()) {
                    IconButton(
                        onClick = { onDone(tx, editedName, selectedCategory) },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).size(32.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text("Merchant Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .animateContentSize()
                    .clickable { isExpanded = !isExpanded }
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tx.rawSms, 
                            fontSize = 11.sp, 
                            color = Color.Gray, 
                            maxLines = if (isExpanded) Int.MAX_VALUE else 2, 
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, 
                            contentDescription = null, 
                            tint = Color.Gray, 
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("SELECT CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 11.sp) },
                        shape = CircleShape
                    )
                }
            }
        }
    }
}

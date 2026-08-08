package com.example.expensetracker.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.MerchantMapping
import com.example.expensetracker.data.TransactionDao
import com.example.expensetracker.logic.SmsScanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(private val dao: TransactionDao) : ViewModel() {

    val allRules: StateFlow<List<MerchantMapping>> = dao.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories = listOf("Food", "Transport", "Shopping", "Groceries", "Bills", "Investment", "Health", "Personal")

    val isScanning = SmsScanner.isScanning
    val scanProgress = SmsScanner.progress

    fun updateRule(rawMerchant: String, newName: String, newCategory: String) {
        viewModelScope.launch {
            dao.applyRuleAndRename(rawMerchant.trim().lowercase(), newName, newCategory)
        }
    }

    fun deleteRule(mapping: MerchantMapping) {
        viewModelScope.launch {
            dao.deleteRule(mapping)
        }
    }

    fun wipeAndRescan(context: Context) {
        viewModelScope.launch {
            dao.clearAllTransactions()
            SmsScanner.scanExistingMessages(context)
        }
    }
}

@Composable
fun RulesManagementScreen(viewModel: RulesViewModel) {
    val rules by viewModel.allRules.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val progress by viewModel.scanProgress.collectAsState()
    
    var ruleToEdit by remember { mutableStateOf<MerchantMapping?>(null) }
    var showWipeDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Learned Rules", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (isScanning) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp
                )
            } else {
                IconButton(onClick = { showWipeDialog = true }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Wipe and Rescan", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        Text(
            text = if (isScanning) "Scanning messages... ${(progress * 100).toInt()}%" else "Rules define how merchants are named and categorized automatically.",
            fontSize = 12.sp,
            color = if (isScanning) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        if (rules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No rules learned yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(rules, key = { it.rawMerchant }) { rule ->
                    RuleRow(
                        rule = rule,
                        onEdit = { ruleToEdit = rule },
                        onDelete = { viewModel.deleteRule(rule) }
                    )
                }
            }
        }
    }

    if (showWipeDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Wipe and Rescan?") },
            text = { Text("This will delete all current transactions and re-scan your inbox using your current rules and updated filters.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.wipeAndRescan(context)
                        showWipeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear & Rescan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") }
            }
        )
    }

    ruleToEdit?.let { mapping ->
        EditRuleDialog(
            mapping = mapping,
            categories = viewModel.categories,
            onDismiss = { ruleToEdit = null },
            onSave = { newName, newCategory ->
                viewModel.updateRule(mapping.rawMerchant, newName, newCategory)
                ruleToEdit = null
            }
        )
    }
}

@Composable
fun RuleRow(rule: MerchantMapping, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = rule.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = "Key: ${rule.rawMerchant}", color = Color.Gray, fontSize = 11.sp)
                Text(text = rule.category, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Rule", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Rule", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun EditRuleDialog(
    mapping: MerchantMapping,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit 
) {
    var editedName by remember { mutableStateOf(mapping.displayName) }
    var selectedCategory by remember { mutableStateOf(mapping.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Rule") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                
                categories.forEach { category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selectedCategory = category }.padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (category == selectedCategory),
                            onClick = { selectedCategory = category }
                        )
                        Text(text = category, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(editedName, selectedCategory) }) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

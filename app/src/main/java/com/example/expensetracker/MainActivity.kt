package com.example.expensetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.logic.SmsScanner
import com.example.expensetracker.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            ), 101)
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                SmsScanner.scanExistingMessages(applicationContext)
            }
        }

        val database = AppDatabase.getInstance(applicationContext)
        val dao = database.transactionDao()

        setContent {
            MaterialTheme {
                MainScreen(dao)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            CoroutineScope(Dispatchers.IO).launch {
                SmsScanner.scanExistingMessages(applicationContext)
            }
        }
    }
}

@Composable
fun MainScreen(dao: com.example.expensetracker.data.TransactionDao) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    selected = currentDestination?.route == "dashboard",
                    onClick = { 
                        navController.navigate("dashboard") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Review") },
                    label = { Text("Review") },
                    selected = currentDestination?.route == "review",
                    onClick = { 
                        navController.navigate("review") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Rules") },
                    label = { Text("Rules") },
                    selected = currentDestination?.route == "rules",
                    onClick = { 
                        navController.navigate("rules") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                val viewModel = remember { DashboardViewModel(dao) }
                DashboardScreen(viewModel)
            }
            composable("review") {
                val viewModel = remember { ReviewViewModel(dao) }
                ReviewScreen(viewModel)
            }
            composable("rules") {
                val viewModel = remember { RulesViewModel(dao) }
                RulesManagementScreen(viewModel)
            }
        }
    }
}

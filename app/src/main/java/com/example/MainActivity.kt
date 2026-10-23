package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PermIdentity
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ScreenMode
import com.example.ui.SkimmsViewModel
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.DocScreen
import com.example.ui.screens.SimulatorScreen
import com.example.ui.screens.WorkerScreen
import com.example.ui.theme.AmberSafety
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Obsidian
import com.example.ui.theme.TacticalCyan

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SkimmsApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkimmsApp(
    viewModel: SkimmsViewModel = viewModel()
) {
    val currentMode by viewModel.screenMode.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Obsidian,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "SKIMMS Emblem",
                            tint = AmberSafety,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SKIMMS",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SMART KAVACH",
                            color = AmberSafety,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("main_bottom_nav"),
                containerColor = DarkSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentMode == ScreenMode.WORKER,
                    onClick = { viewModel.setScreenMode(ScreenMode.WORKER) },
                    icon = { Icon(Icons.Default.PermIdentity, contentDescription = "Worker") },
                    label = { Text("Worker", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = AmberSafety,
                        indicatorColor = AmberSafety,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_worker")
                )

                NavigationBarItem(
                    selected = currentMode == ScreenMode.ADMIN,
                    onClick = { viewModel.setScreenMode(ScreenMode.ADMIN) },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Admin") },
                    label = { Text("Control Room", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = TacticalCyan,
                        indicatorColor = TacticalCyan,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_admin")
                )

                NavigationBarItem(
                    selected = currentMode == ScreenMode.SIMULATOR,
                    onClick = { viewModel.setScreenMode(ScreenMode.SIMULATOR) },
                    icon = { Icon(Icons.Default.Science, contentDescription = "Simulator") },
                    label = { Text("Test Bed", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = AmberSafety,
                        indicatorColor = AmberSafety,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_simulator")
                )

                NavigationBarItem(
                    selected = currentMode == ScreenMode.DOCS,
                    onClick = { viewModel.setScreenMode(ScreenMode.DOCS) },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = "Docs") },
                    label = { Text("B.Tech Guide", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = TacticalCyan,
                        indicatorColor = TacticalCyan,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_docs")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentMode) {
                ScreenMode.WORKER -> WorkerScreen(viewModel = viewModel)
                ScreenMode.ADMIN -> AdminScreen(viewModel = viewModel)
                ScreenMode.SIMULATOR -> SimulatorScreen(viewModel = viewModel)
                ScreenMode.DOCS -> DocScreen()
            }
        }
    }
}

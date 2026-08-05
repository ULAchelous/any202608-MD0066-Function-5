package io.ula.aiy.mb

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.ula.aiy.mb.config.CONTENT_TYPE
import io.ula.aiy.mb.config.SettingContent
import io.ula.aiy.mb.config.SettingManager
import io.ula.aiy.mb.fw.FloatingWindowService
import io.ula.aiy.mb.ui.theme.MythBustersTheme
import io.ula.aiy.mb.ui.ProfileCard

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Main : Screen("main", "Main", Icons.Default.Home)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingManager = SettingManager(this)
        settingManager.register(listOf(
            SettingContent("floating_window", 0, CONTENT_TYPE.BOOL) {
                (it.toInt() == 0 || it.toInt() == 1) && (it.toInt() == 0 || Settings.canDrawOverlays(this))
            },
            SettingContent("auto_monitoring", 0, CONTENT_TYPE.BOOL) { it.toInt() == 0 || it.toInt() == 1 },
            SettingContent("image_search", 0, CONTENT_TYPE.BOOL) { it.toInt() == 0 || it.toInt() == 1 }
        ))

        if (settingManager.getEntry("floating_window")?.getBoolean() == true && Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingWindowService::class.java))
        }

        enableEdgeToEdge()
        setContent {

            MythBustersTheme {
                val navController = rememberNavController()
                val screens = listOf(Screen.Main, Screen.Settings)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            screens.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = null) },
                                    label = { Text(screen.label) },
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Main.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Main.route) { MainPage() }
                        composable(Screen.Settings.route) { SettingsPage(settingManager = settingManager) }
                    }
                }
            }
        }
    }
}

@Composable
fun MainPage(modifier: Modifier = Modifier) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val windowsHeightDp = with(density){
        windowInfo.containerSize.height.toDp()
    }
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .height((windowsHeightDp.value * 0.05).dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Icon(
                    Icons.Default.Home,
                    "Home Page Icon",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "概览", 
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            ProfileCard()
        }
    }
}

@Composable
fun SettingsPage(modifier: Modifier = Modifier, settingManager: SettingManager) {
    val context = LocalContext.current

    val floatingWindowEnabled = remember { mutableStateOf(settingManager.getEntry("floating_window")?.getBoolean() ?: false) }
    val autoMonitoringEnabled = remember { mutableStateOf(settingManager.getEntry("auto_monitoring")?.getBoolean() ?: false) }
    val imageSearchEnabled = remember { mutableStateOf(settingManager.getEntry("image_search")?.getBoolean() ?: false) }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ){
                Icon(
                    Icons.Default.Settings, 
                    contentDescription = "setting",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "设置", 
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }


            SettingSwitchRow("启用悬浮窗", floatingWindowEnabled.value) { enabled ->
                floatingWindowEnabled.value = enabled
                settingManager.updateSetting("floating_window", if (enabled) 1 else 0)
                
                // Logic for starting/stopping service based on floating window setting
                if (enabled) {
                    if (Settings.canDrawOverlays(context)) {
                        context.startService(Intent(context, FloatingWindowService::class.java))
                    } else {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    }
                } else {
                    context.stopService(Intent(context, FloatingWindowService::class.java))
                }
            }
            
            SettingSwitchRow("消息自动监测", autoMonitoringEnabled.value) { enabled ->
                autoMonitoringEnabled.value = enabled
                settingManager.updateSetting("auto_monitoring", if (enabled) 1 else 0)
            }
            
            SettingSwitchRow("启用图片检索", imageSearchEnabled.value) { enabled ->
                imageSearchEnabled.value = enabled
                settingManager.updateSetting("image_search", if (enabled) 1 else 0)
            }

        }
    }
}

@Composable
fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

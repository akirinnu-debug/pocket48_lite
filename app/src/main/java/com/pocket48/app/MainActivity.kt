package com.pocket48.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pocket48.app.ui.about.AboutScreen
import com.pocket48.app.ui.download.DownloadScreen
import com.pocket48.app.ui.history.HistoryScreen
import com.pocket48.app.ui.live.LiveListScreen
import com.pocket48.app.ui.live.LivePlayScreen
import com.pocket48.app.ui.members.MemberListScreen
import com.pocket48.app.ui.theme.Pocket48Theme

class MainActivity : ComponentActivity() {

    /** Android 13+ 通知权限运行时申请 */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果忽略: 不允许时下载仍可执行, 仅无通知 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Pocket48Theme {
                Pocket48LiteApp()
            }
        }
        // Android 13+ 需运行时申请 POST_NOTIFICATIONS, 否则下载通知不显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private val bottomNavRoutes = setOf("live", "members", "history", "downloads", "about")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pocket48LiteApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    // Tab 顺序: 直播 / 成员 / 历史 / 下载 / 关于
                    // (历史与下载移到成员后面, 更符合 "先找成员→看历史→管下载" 的操作习惯)
                    NavigationBarItem(
                        selected = currentRoute == "live",
                        onClick = {
                            navController.navigate("live") {
                                popUpTo("live") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.LiveTv, contentDescription = "直播") },
                        label = { Text("直播") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "members",
                        onClick = {
                            navController.navigate("members") {
                                popUpTo("live") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.People, contentDescription = "成员") },
                        label = { Text("成员") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "history",
                        onClick = {
                            navController.navigate("history") {
                                popUpTo("live") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = "历史") },
                        label = { Text("历史") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "downloads",
                        onClick = {
                            navController.navigate("downloads") {
                                popUpTo("live") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Download, contentDescription = "下载") },
                        label = { Text("下载") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "about",
                        onClick = {
                            navController.navigate("about") {
                                popUpTo("live") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Info, contentDescription = "关于") },
                        label = { Text("关于") },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "live",
            modifier = Modifier.padding(padding),
        ) {
            composable("live") {
                LiveListScreen(onLiveClick = { liveId ->
                    navController.navigate("play/$liveId")
                })
            }
            composable("members") {
                MemberListScreen()
            }
            composable("history") {
                HistoryScreen(onLiveClick = { liveId ->
                    navController.navigate("play/$liveId")
                })
            }
            composable("downloads") {
                DownloadScreen(onLiveClick = { liveId ->
                    navController.navigate("play/$liveId")
                })
            }
            composable(
                route = "play/{liveId}",
                arguments = listOf(navArgument("liveId") { type = NavType.StringType }),
            ) { entry ->
                val liveId = entry.arguments?.getString("liveId") ?: ""
                LivePlayScreen(liveId = liveId, onBack = { navController.popBackStack() })
            }
            composable("about") {
                AboutScreen()
            }
        }
    }
}


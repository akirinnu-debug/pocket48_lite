package com.pocket48.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pocket48.app.ui.about.AboutScreen
import com.pocket48.app.ui.live.LiveListScreen
import com.pocket48.app.ui.live.LivePlayScreen
import com.pocket48.app.ui.members.MemberListScreen
import com.pocket48.app.ui.theme.Pocket48Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Pocket48Theme {
                Pocket48LiteApp()
            }
        }
    }
}

private val bottomNavRoutes = setOf("live", "members", "about")

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
            composable(
                route = "play/{liveId}",
                arguments = listOf(navArgument("liveId") { type = NavType.StringType }),
            ) { entry ->
                val liveId = entry.arguments?.getString("liveId") ?: ""
                LivePlayScreen(liveId = liveId, onBack = { navController.popBackStack() })
            }
            composable("members") {
                MemberListScreen()
            }
            composable("about") {
                AboutScreen()
            }
        }
    }
}


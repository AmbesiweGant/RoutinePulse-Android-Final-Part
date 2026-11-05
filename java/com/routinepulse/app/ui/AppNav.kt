package com.routinepulse.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.routinepulse.app.AppContainer
import com.routinepulse.app.R
import com.routinepulse.app.data.local.ThemeMode
import com.routinepulse.app.ui.components.LoadingBox
import com.routinepulse.app.ui.screens.*
import com.routinepulse.app.ui.theme.RoutinePulseTheme
import com.routinepulse.app.ui.viewmodel.*
import com.routinepulse.app.push.NotificationHelper
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val NOTES = "notes"
    const val QUIZ = "quiz"
    const val SETTINGS = "settings"
    const val PROGRESS = "progress"
    const val HABIT_EDIT = "habit_edit?habitId={habitId}"
    const val NOTE_EDIT = "note_edit?noteId={noteId}"
    const val QUIZ_PLAY = "quiz_play/{quizId}"

    fun habitEdit(id: String?) = if (id == null) "habit_edit" else "habit_edit?habitId=$id"
    fun noteEdit(id: String?) = if (id == null) "note_edit" else "note_edit?noteId=$id"
    fun quizPlay(id: String) = "quiz_play/$id"
}

private data class BottomItem(val route: String, val labelRes: Int, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, R.string.nav_home, Icons.Default.Home),
    BottomItem(Routes.NOTES, R.string.nav_notes, Icons.Default.Edit),
    BottomItem(Routes.QUIZ, R.string.nav_quiz, Icons.Default.Star),
    BottomItem(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings)
)

/** Root composable: applies the saved theme and decides between the login flow and the main app. */
@Composable
fun AppRoot(container: AppContainer) {
    val themeMode by container.settingsStore.themeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val loggedInFlow = remember { container.settingsStore.tokenFlow.map { !it.isNullOrBlank() }.distinctUntilChanged() }
    val loggedIn by loggedInFlow.collectAsState(initial = null)

    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    RoutinePulseTheme(darkTheme = dark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val state = loggedIn
            if (state == null) {
                LoadingBox() // brief moment while DataStore loads the saved session
            } else {
                // startLoggedIn is captured once, so later logins/logouts navigate instead of rebuilding the graph.
                MainNav(container, startLoggedIn = remember { state }, loggedIn = state)
            }
        }
    }
}

@Composable
private fun MainNav(container: AppContainer, startLoggedIn: Boolean, loggedIn: Boolean) {
    val navController = rememberNavController()
    val factory = remember { ViewModelFactory(container) }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // Push notifications: register this phone after login, and ask for the Android 13+ permission once.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            container.settingsStore.setPushPermissionAsked(true)
            container.settingsStore.setNotifications(granted)
            container.pushManager.register()
        }
    }
    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            container.pushManager.register()
            val asked = container.settingsStore.pushPermissionAskedFlow.first()
            if (!asked && !NotificationHelper.hasPermission(context)) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // React to login / logout (including the server rejecting an expired token).
    LaunchedEffect(loggedIn) {
        val current = navController.currentDestination?.route ?: return@LaunchedEffect
        if (!loggedIn && current != Routes.AUTH) {
            navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
        } else if (loggedIn && current == Routes.AUTH) {
            navController.navigate(Routes.HOME) { popUpTo(Routes.AUTH) { inclusive = true } }
        }
    }

    val showBar = route in setOf(Routes.HOME, Routes.NOTES, Routes.QUIZ, Routes.SETTINGS)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = { if (showBar) BottomBar(navController, route) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (startLoggedIn) Routes.HOME else Routes.AUTH,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.AUTH) {
                val vm: AuthViewModel = viewModel(factory = factory)
                AuthScreen(vm)
            }
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(factory = factory)
                val name by container.settingsStore.displayNameFlow.collectAsState(initial = "")
                HomeScreen(
                    vm = vm,
                    userName = name,
                    onAddHabit = { navController.navigate(Routes.habitEdit(null)) },
                    onEditHabit = { navController.navigate(Routes.habitEdit(it)) },
                    onOpenProgress = { navController.navigate(Routes.PROGRESS) }
                )
            }
            composable(
                Routes.HABIT_EDIT,
                arguments = listOf(navArgument("habitId") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                val vm: HabitEditViewModel = viewModel(factory = factory)
                HabitEditScreen(vm, entry.arguments?.getString("habitId")) { navController.popBackStack() }
            }
            composable(Routes.NOTES) {
                val vm: NotesViewModel = viewModel(factory = factory)
                NotesScreen(
                    vm = vm,
                    onOpenNote = { navController.navigate(Routes.noteEdit(it)) },
                    onNewNote = { navController.navigate(Routes.noteEdit(null)) }
                )
            }
            composable(
                Routes.NOTE_EDIT,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                val vm: NoteEditViewModel = viewModel(factory = factory)
                NoteEditScreen(
                    vm = vm,
                    noteId = entry.arguments?.getString("noteId"),
                    onBack = { navController.popBackStack() },
                    onQuizReady = { navController.navigate(Routes.quizPlay(it)) }
                )
            }
            composable(Routes.QUIZ) {
                val vm: QuizHubViewModel = viewModel(factory = factory)
                QuizHubScreen(vm) { navController.navigate(Routes.quizPlay(it)) }
            }
            composable(
                Routes.QUIZ_PLAY,
                arguments = listOf(navArgument("quizId") { type = NavType.StringType })
            ) { entry ->
                val vm: QuizPlayViewModel = viewModel(factory = factory)
                QuizPlayScreen(vm, entry.arguments?.getString("quizId").orEmpty()) { navController.popBackStack() }
            }
            composable(Routes.PROGRESS) {
                val vm: ProgressViewModel = viewModel(factory = factory)
                ProgressScreen(vm) { navController.popBackStack() }
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        bottomItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

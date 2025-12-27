package com.smartpresence.idukay.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartpresence.idukay.presentation.attendance.AttendanceScreen
import com.smartpresence.idukay.presentation.course.CourseSelectionScreen
import com.smartpresence.idukay.presentation.diagnostics.DiagnosticsScreen
import com.smartpresence.idukay.presentation.login.LoginScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object CourseSelection : Screen("courses")
    object Attendance : Screen("attendance/{courseId}") {
        fun createRoute(courseId: String) = "attendance/$courseId"
    }
    object Diagnostics : Screen("diagnostics")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.CourseSelection.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.CourseSelection.route) {
            CourseSelectionScreen(
                onCourseSelected = { courseId ->
                    navController.navigate(Screen.Attendance.createRoute(courseId))
                }
            )
        }
        
        composable(
            route = Screen.Attendance.route,
            arguments = listOf(
                navArgument("courseId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
            AttendanceScreen(
                courseId = courseId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) }
            )
        }
        
        composable(Screen.Diagnostics.route) {
            DiagnosticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

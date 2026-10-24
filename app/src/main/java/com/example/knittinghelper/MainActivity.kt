package com.example.knittinghelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.knittinghelper.ui.chart.ChartReaderScreen
import com.example.knittinghelper.ui.pdfstep.PdfStepScreen
import com.example.knittinghelper.ui.project.ProjectListScreen
import com.example.knittinghelper.ui.theme.KnittingHelperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KnittingHelperTheme {
                KnittingHelperApp()
            }
        }
    }
}

@Composable
fun KnittingHelperApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "project_list",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("project_list") {
            ProjectListScreen(
                onOpenPdfSteps = { projectId ->
                    navController.navigate("pdf_steps/$projectId")
                },
                onOpenChartReader = { projectId ->
                    navController.navigate("chart_reader/$projectId")
                }
            )
        }

        composable(
            route = "pdf_steps/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            PdfStepScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "chart_reader/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            ChartReaderScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

package com.healthtrend.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.healthtrend.ui.compare.CompareScreen
import com.healthtrend.ui.dashboard.DashboardScreen
import com.healthtrend.ui.detail.MetricDetailScreen
import com.healthtrend.ui.nutrition.NutritionScreen

/**
 * The app's route table.
 *
 * Plain string routes rather than type-safe navigation objects: the navigation graph is four
 * destinations wide, and adding `kotlinx-serialization` for it would be a plugin and a dependency
 * for no readability gain.
 */
internal object Routes {
    const val DASHBOARD = "dashboard"
    const val COMPARE = "compare"
    const val NUTRITION = "nutrition"

    const val METRIC_ID_ARG = "metricId"
    const val METRIC_DETAIL = "detail/{$METRIC_ID_ARG}"

    fun metricDetail(metricId: String): String = "detail/$metricId"
}

/**
 * Wires the four Phase 3 destinations together.
 *
 * A route argument is the only thing the ViewModels cannot be given through Koin, so the detail
 * screen receives its `metricId` here and forwards it from a `LaunchedEffect`.
 */
@Composable
internal fun HealthTrendNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier,
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenMetric = { metricId -> navController.navigate(Routes.metricDetail(metricId)) },
                onOpenCompare = { navController.navigate(Routes.COMPARE) },
                onOpenNutrition = { navController.navigate(Routes.NUTRITION) },
            )
        }
        composable(
            route = Routes.METRIC_DETAIL,
            arguments = listOf(navArgument(Routes.METRIC_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            MetricDetailScreen(
                metricId = entry.arguments?.getString(Routes.METRIC_ID_ARG).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.COMPARE) {
            CompareScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.NUTRITION) {
            NutritionScreen(onBack = { navController.popBackStack() })
        }
    }
}

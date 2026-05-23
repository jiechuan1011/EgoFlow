package com.egoflow.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.egoflow.app.ui.focus.FocusFirewallScreen
import com.egoflow.app.ui.coach.ChatCoachScreen
import com.egoflow.app.ui.evolution.EvolutionCenterScreen
import com.egoflow.app.ui.timeline.ScheduleTimelineScreen

object Routes {
    const val FOCUS = "focus"
    const val COACH = "coach"
    const val EVOLUTION = "evolution"
    const val TIMELINE = "timeline"
}

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.FOCUS
    ) {
        composable(Routes.FOCUS) {
            FocusFirewallScreen(
                onNavigateToCoach = { navController.navigate(Routes.COACH) },
                onNavigateToTimeline = { navController.navigate(Routes.TIMELINE) },
                onNavigateToEvolution = { navController.navigate(Routes.EVOLUTION) }
            )
        }
        composable(Routes.COACH) {
            ChatCoachScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.EVOLUTION) {
            EvolutionCenterScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TIMELINE) {
            ScheduleTimelineScreen(onBack = { navController.popBackStack() })
        }
    }
}

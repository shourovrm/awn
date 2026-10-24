package app.awn.quran

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.awn.quran.feature.duas.topics.TopicsScreen
import app.awn.quran.feature.duas.ui.DuasScreen
import app.awn.quran.services.AwnServices

/** Duas from the Quran and Ayahs by topic; a topic's related duas open the Duas group. */
internal fun NavGraphBuilder.collectionDestinations(
    services: AwnServices,
    navController: NavController,
) {
    composable(
        Routes.DUAS,
        arguments =
            listOf(
                navArgument(Routes.DUAS_GROUP_ARGUMENT) {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) { entry ->
        DuasScreen(
            dependencies = services.duasDependencies,
            onBack = { navController.popBackStack() },
            onOpenQuran = { page, ayah -> navController.openQuran(page, ayah) },
            initialGroupKey = entry.arguments?.getString(Routes.DUAS_GROUP_ARGUMENT),
        )
    }
    composable(Routes.TOPICS) {
        TopicsScreen(
            dependencies = services.duasDependencies,
            onBack = { navController.popBackStack() },
            onOpenQuran = { page, ayah -> navController.openQuran(page, ayah) },
            onOpenDuasGroup = { group -> navController.navigate(Routes.duas(group)) },
        )
    }
}

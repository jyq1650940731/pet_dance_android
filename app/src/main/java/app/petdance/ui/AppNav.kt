package app.petdance.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.petdance.data.GenerationDto
import app.petdance.domain.isActiveStatus
import app.petdance.ui.screens.CreateScreen
import app.petdance.ui.screens.DetailScreen
import app.petdance.ui.screens.FailedScreen
import app.petdance.ui.screens.GeneratingScreen
import app.petdance.ui.screens.GuideScreen
import app.petdance.ui.screens.HomeScreen
import app.petdance.ui.screens.LegalScreen
import app.petdance.ui.screens.LoginScreen
import app.petdance.ui.screens.MeScreen
import app.petdance.ui.screens.OrdersScreen
import app.petdance.ui.screens.PayScreen
import app.petdance.ui.screens.ReportScreen
import app.petdance.ui.screens.ResultScreen
import app.petdance.ui.screens.SettingsScreen
import app.petdance.ui.screens.WelcomeScreen
import app.petdance.ui.screens.WorksScreen
import app.petdance.ui.theme.AppBackground
import app.petdance.ui.theme.Card
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppRoot() {
    val session = activityViewModel<SessionViewModel>()
    val state by session.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        session.messages.collectLatest { snackbar.showSnackbar(it) }
    }
    if (state.booting) {
        Scaffold(containerColor = AppBackground) { padding ->
            Text("pet_dance", modifier = Modifier.padding(padding).padding(24.dp))
        }
        return
    }
    val start = when {
        !state.consented -> "welcome"
        !state.guideDone -> "guide"
        else -> "home"
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val leaf = entry?.destination?.route.orEmpty().substringBefore("?").substringBefore("/")
    val showBar = leaf in setOf("home", "me", "works", "orders", "settings")
    val meSelected = leaf in setOf("me", "works", "orders", "settings")
    LaunchedEffect(state.consented) {
        if (!state.consented && leaf.isNotBlank() && leaf != "welcome" && leaf != "legal") {
            nav.navigate("welcome") { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }
    Scaffold(
        containerColor = AppBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Card) {
                    NavigationBarItem(
                        selected = !meSelected,
                        onClick = { tab(nav, "home") },
                        label = { Text("首页") },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    )
                    NavigationBarItem(
                        selected = meSelected,
                        onClick = {
                            if (session.state.value.loggedIn) tab(nav, "me") else nav.navigate(login("me"))
                        },
                        label = { Text("我的") },
                        icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = start, modifier = Modifier.padding(padding)) {
            composable("welcome") {
                WelcomeScreen(session, onStart = { nav.navigate("guide") }, onLegal = { nav.navigate("legal/$it") })
            }
            composable("guide") {
                GuideScreen(session) {
                    nav.navigate("home") { popUpTo(nav.graph.id) { inclusive = true } }
                }
            }
            composable("legal/{doc}", listOf(navArgument("doc") { type = NavType.StringType })) {
                LegalScreen(it.arguments?.getString("doc") ?: "terms") { nav.popBackStack() }
            }
            composable("home") {
                HomeScreen(
                    session = session,
                    onOpen = { nav.navigate("detail/${it.id}") },
                    onWorks = {
                        if (session.state.value.loggedIn) nav.navigate("works") else nav.navigate(login("works"))
                    },
                    onAccount = {
                        if (session.state.value.loggedIn) nav.navigate("pay") else nav.navigate(login("pay"))
                    },
                    onBuy = { nav.navigate("pay") },
                )
            }
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                DetailScreen(id, onBack = { nav.popBackStack() }, onCreate = { nav.navigate("create/${it.id}") })
            }
            composable("create/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                CreateScreen(
                    id = id,
                    session = session,
                    onBack = { nav.popBackStack() },
                    onLogin = { cost -> nav.navigate(login("create", id, cost)) },
                    onPay = { nav.navigate("pay") },
                    onCreated = { taskId ->
                        nav.navigate("generating/$taskId") { popUpTo("create/$id") { inclusive = true } }
                    },
                )
            }
            composable(
                "login?next={next}&id={id}&cost={cost}",
                listOf(
                    navArgument("next") { type = NavType.StringType; defaultValue = "home" },
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("cost") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { entry ->
                val next = entry.arguments?.getString("next") ?: "home"
                val cost = entry.arguments?.getInt("cost") ?: 0
                LoginScreen(session, onBack = { nav.popBackStack() }) { token ->
                    session.onLoggedIn(token)
                    nav.popBackStack()
                    when (next) {
                        "works" -> nav.navigate("works")
                        "me" -> nav.navigate("me")
                        "orders" -> nav.navigate("orders")
                        "settings" -> nav.navigate("settings")
                        "pay" -> nav.navigate("pay")
                        "create" -> {
                            val balance = token.user?.credits?.balance ?: 0
                            if (cost > 0 && balance < cost) nav.navigate("pay")
                        }
                    }
                }
            }
            composable("generating/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                GeneratingScreen(
                    id = id,
                    onWorks = { nav.navigate("works") },
                    onSucceeded = {
                        nav.navigate("result/$id") { popUpTo("generating/$id") { inclusive = true } }
                    },
                    onFailed = {
                        nav.navigate("failed/$id") { popUpTo("generating/$id") { inclusive = true } }
                    },
                )
            }
            composable("result/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val user = session.state.collectAsStateWithLifecycle().value
                ResultScreen(
                    id = id,
                    showRate = user.user?.successfulCount == 1 && !user.rateDismissed,
                    onBackHome = { openHome(nav) },
                    onAgain = { templateId -> nav.navigate("create/$templateId") },
                    onReport = { nav.navigate("report/$id") },
                    onRate = {
                        session.dismissRate()
                        openStore(context) { session.toast(it) }
                    },
                    onDismissRate = { session.dismissRate() },
                    onDeleted = { nav.popBackStack() },
                    onToast = session::toast,
                )
            }
            composable("failed/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                FailedScreen(
                    id = id,
                    onRetry = { templateId ->
                        if (templateId.isNotBlank()) nav.navigate("create/$templateId") { popUpTo("failed/$id") { inclusive = true } }
                    },
                    onHome = { openHome(nav) },
                )
            }
            composable("me") {
                MeScreen(
                    session = session,
                    onLogin = { nav.navigate(login("me")) },
                    onWorks = { nav.navigate("works") },
                    onOrders = { nav.navigate("orders") },
                    onSettings = { nav.navigate("settings") },
                    onBuy = { nav.navigate("pay") },
                )
            }
            composable("works") {
                WorksScreen(
                    onBack = { nav.popBackStack() },
                    onToast = session::toast,
                    onOpen = { item -> nav.navigate(destinationFor(item)) },
                )
            }
            composable("orders") { OrdersScreen { nav.popBackStack() } }
            composable("pay") {
                PayScreen(session, onBack = { nav.popBackStack() }, onLogin = { nav.navigate(login("pay")) })
            }
            composable("settings") {
                SettingsScreen(
                    session = session,
                    onBack = { nav.popBackStack() },
                    onLegal = { nav.navigate("legal/$it") },
                    onLoggedOut = { openHome(nav) },
                )
            }
            composable("report/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ReportScreen(id, title = "作品", onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun destinationFor(item: GenerationDto): String = when {
    item.status == "failed" -> "failed/${item.id}"
    isActiveStatus(item.status) -> "generating/${item.id}"
    else -> "result/${item.id}"
}

private fun login(next: String, id: String = "", cost: Int = 0) = "login?next=$next&id=$id&cost=$cost"

private fun tab(nav: NavHostController, dest: String) {
    nav.navigate(dest) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun openHome(nav: NavHostController) {
    nav.navigate("home") {
        popUpTo(nav.graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun openStore(context: android.content.Context, onMissing: (String) -> Unit) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        onMissing("未找到应用商店")
    }
}

@Composable
private inline fun <reified T : ViewModel> activityViewModel(): T {
    val activity = LocalContext.current as ComponentActivity
    return koinViewModel(viewModelStoreOwner = activity)
}

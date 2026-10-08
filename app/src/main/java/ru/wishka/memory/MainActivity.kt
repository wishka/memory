package ru.wishka.memory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.Tab
import ru.wishka.memory.ui.chats.ChatScreen
import ru.wishka.memory.ui.chats.ChatsScreen
import ru.wishka.memory.ui.components.BottomBar
import ru.wishka.memory.ui.components.CosmicBackground
import ru.wishka.memory.ui.feed.FeedScreen
import ru.wishka.memory.ui.profile.ProfileScreen
import ru.wishka.memory.ui.splash.AtomSplash
import ru.wishka.memory.ui.theme.MemoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Рисуем под системными панелями; значки панелей — светлые
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MemoryTheme { AppRoot() }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    // rememberSaveable: при повороте или возврате в приложение заставка
    // не показывается заново — только при холодном запуске
    var splashDone by rememberSaveable { mutableStateOf(false) }
    Crossfade(targetState = splashDone, animationSpec = tween(500), label = "splash") { done ->
        if (done) MainScreen(vm) else AtomSplash(onFinished = { splashDone = true })
    }
}

@Composable
private fun MainScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
    }

    // «Назад»: закрыть переписку → вернуться на ленту → выйти
    BackHandler(enabled = state.openChatId != null || state.tab != Tab.Feed) { vm.back() }

    Box(modifier = Modifier.fillMaxSize()) {
        CosmicBackground()

        val openChat = state.openChat
        if (openChat != null) {
            ChatScreen(vm, openChat)
        } else {
        // Отступы списков: сверху — под строку состояния, снизу — под
        // парящую панель вкладок, чтобы последняя карточка не пряталась
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
        val padding = PaddingValues(top = top, bottom = bottom)

        AnimatedContent(
            targetState = state.tab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "tab",
        ) { tab ->
            when (tab) {
                Tab.Feed -> FeedScreen(vm, state, padding)
                Tab.Chats -> ChatsScreen(vm, state, padding)
                Tab.Profile -> ProfileScreen(vm, state, padding, version)
            }
        }

        BottomBar(
            selected = state.tab,
            unread = state.unreadTotal,
            onSelect = vm::selectTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        )
        }
    }
}

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
import androidx.compose.runtime.LaunchedEffect
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
import ru.wishka.memory.data.ProfileInfo
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.groups.GroupScreen
import ru.wishka.memory.ui.groups.GroupsScreen
import ru.wishka.memory.ui.person.PersonScreen
import ru.wishka.memory.ui.reels.ReelsScreen
import ru.wishka.memory.ui.auth.AuthScreen
import ru.wishka.memory.ui.auth.AuthViewModel
import ru.wishka.memory.ui.stories.StoriesScreen
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

/** Что сейчас на экране: заставка, вход или само приложение. */
private enum class Stage { Splash, Auth, Main }

@Composable
fun AppRoot(vm: AppViewModel = viewModel(), authVm: AuthViewModel = viewModel()) {
    val auth by authVm.state.collectAsState()
    // rememberSaveable: при повороте или возврате в приложение заставка
    // не показывается заново — только при холодном запуске
    var splashDone by rememberSaveable { mutableStateOf(false) }

    // Вошедший человек — в профиль и в свои записи
    val account = auth.account
    LaunchedEffect(account) {
        if (account != null) vm.setAccount(account.username, account.displayName, account.info) else vm.reset()
    }

    // Заставка держится, пока проверяется сохранённая сессия: иначе на
    // долю секунды мелькнул бы экран входа у уже вошедшего человека
    val stage = when {
        !splashDone || auth.checking -> Stage.Splash
        account == null -> Stage.Auth
        else -> Stage.Main
    }
    Crossfade(targetState = stage, animationSpec = tween(500), label = "stage") { s ->
        when (s) {
            Stage.Splash -> AtomSplash(onFinished = { splashDone = true })
            Stage.Auth -> AuthScreen(authVm, auth)
            Stage.Main -> MainScreen(vm, onSaveProfile = authVm::saveProfile, onLogout = authVm::logout)
        }
    }
}

@Composable
private fun MainScreen(vm: AppViewModel, onSaveProfile: (String, ProfileInfo) -> Unit, onLogout: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
    }

    // «Назад»: переписка → страница человека → группа → лента → выход
    val canGoBack = state.openChatId != null || state.openPersonId != null ||
        state.openGroupId != null || state.tab != Tab.Feed
    BackHandler(enabled = canGoBack) { vm.back() }

    Box(modifier = Modifier.fillMaxSize()) {
        CosmicBackground()

        // Отступы списков: сверху — под строку состояния, снизу — под
        // парящую панель вкладок, чтобы последняя карточка не пряталась
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
        val padding = PaddingValues(top = top, bottom = bottom)

        val openChat = state.openChat
        val openPerson = state.openPerson
        val openGroup = state.openGroup
        if (openChat != null) {
            ChatScreen(vm, openChat)
        } else {
        when {
            // Страница человека и группы — поверх вкладки, панель вкладок остаётся
            openPerson != null -> PersonScreen(vm, state, openPerson, padding)
            openGroup != null -> GroupScreen(vm, state, openGroup, padding)
            else -> AnimatedContent(
                targetState = state.tab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "tab",
            ) { tab ->
                when (tab) {
                    Tab.Feed -> FeedScreen(vm, state, padding)
                    Tab.Stories -> StoriesScreen(vm, state, padding)
                    Tab.Reels -> ReelsScreen(vm, state, padding)
                    Tab.Chats -> ChatsScreen(vm, state, padding)
                    Tab.Groups -> GroupsScreen(vm, state, padding)
                    Tab.Profile -> ProfileScreen(vm, state, padding, version, onSaveProfile, onLogout)
                }
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

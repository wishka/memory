package ru.wishka.memory

import android.os.Bundle
import android.widget.Toast
import ru.wishka.memory.i18n.t
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
import ru.wishka.memory.ui.together.TogetherScreen
import ru.wishka.memory.ui.groups.GroupScreen
import ru.wishka.memory.ui.groups.GroupSettingsScreen
import ru.wishka.memory.ui.groups.GroupsScreen
import ru.wishka.memory.ui.person.PersonScreen
import ru.wishka.memory.ui.prism.PrismScreen
import ru.wishka.memory.ui.components.CommentsSheet
import ru.wishka.memory.ui.components.MediaViewer
import ru.wishka.memory.data.StateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
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
import ru.wishka.memory.ui.profile.ProfileSectionScreen
import ru.wishka.memory.ui.profile.MusicScreen
import ru.wishka.memory.ui.profile.StickerStudioScreen
import ru.wishka.memory.ui.profile.RecapScreen
import ru.wishka.memory.ui.profile.ShelfScreen
import ru.wishka.memory.ui.prism.create.PrismCreateFlow
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.settings.AccountActions
import ru.wishka.memory.ui.settings.SettingsScreen
import ru.wishka.memory.data.DevicePrefs
import ru.wishka.memory.ui.splash.AtomSplash
import ru.wishka.memory.ui.theme.MemoryTheme
import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.MutableStateFlow
import ru.wishka.memory.data.Nudge
import ru.wishka.memory.push.AppVisibility
import ru.wishka.memory.push.MemoryNotifications
import ru.wishka.memory.push.PushBus
import ru.wishka.memory.ui.components.NudgeBanner
import ru.wishka.memory.ui.components.ConsentGate
import ru.wishka.memory.ui.components.LegalDocViewer
import ru.wishka.memory.ui.components.ReportDialog
import ru.wishka.memory.data.LegalDoc
import ru.wishka.memory.data.SoundPlayer
import ru.wishka.memory.data.SoundPrefs
import ru.wishka.memory.ui.components.EchoDialog
import ru.wishka.memory.ui.components.InviteDialog
import ru.wishka.memory.ui.auth.OnboardingScreen
import ru.wishka.memory.data.Invite
import ru.wishka.memory.data.Invites

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
    }

    override fun onStop() {
        super.onStop()
        AppVisibility.foreground = false
    }

    /** Нажали на уведомление, когда приложение уже открыто. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        MemoryNotifications.fromIntent(intent)?.let { openedNudge.value = it }
        intent.data?.toString()?.let(Invites::parse)?.let { openedInvite.value = it }
    }

    companion object {
        /** Уведомление, по которому открыли приложение, — его откроет AppRoot. */
        val openedNudge = MutableStateFlow<Nudge?>(null)

        /** Ссылка-приглашение, по которой открыли приложение: её разберёт MainScreen после входа. */
        val openedInvite = MutableStateFlow<Invite?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Только при запуске нажатием на уведомление: не при повороте и не из «Недавних»
        val fromHistory = (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) {
            MemoryNotifications.fromIntent(intent)?.let { openedNudge.value = it }
            intent?.data?.toString()?.let(Invites::parse)?.let { openedInvite.value = it }
        }
        // Рисуем под системными панелями; значки панелей — светлые
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Язык и размер текста — до первого экрана, чтобы не мигали
        DevicePrefs(applicationContext).load()
        setContent {
            MemoryTheme { AppRoot() }
        }
    }
}

/** Что сейчас на экране: заставка, вход или само приложение. */
private enum class Stage { Splash, Onboarding, Auth, Consent, Main }

@Composable
fun AppRoot(vm: AppViewModel = viewModel(), authVm: AuthViewModel = viewModel()) {
    val auth by authVm.state.collectAsState()
    // rememberSaveable: при повороте или возврате в приложение заставка
    // не показывается заново — только при холодном запуске
    var splashDone by rememberSaveable { mutableStateOf(false) }

    // Вошедший человек — в профиль и в свои записи; его записи, переписка
    // и закладки — из файла на телефоне
    val account = auth.account
    val context = LocalContext.current
    val store = remember(context) { StateStore(context.applicationContext) }
    val device = remember(context) { DevicePrefs(context.applicationContext) }
    // Знакомство «выберите любимое» — один раз на телефоне, до регистрации
    var onboarded by rememberSaveable { mutableStateOf(device.onboarded) }
    var registerNext by rememberSaveable { mutableStateOf(false) }
    var loadedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(account) {
        loadedFor = null
        if (account != null) {
            vm.setAccount(account.username, account.displayName, account.info)
            val me = vm.state.value.me
            val saved = withContext(Dispatchers.IO) { store.load(account.username, me) }
            saved?.let(vm::restore)
            // Любимое из знакомства — только на полку нового аккаунта (сохранённого ещё нет).
            // Вошли в старый — выбор ждёт следующей регистрации на этом телефоне.
            if (saved == null) {
                device.pendingPicks().takeIf { it.isNotEmpty() }?.let { picks ->
                    vm.addFavorites(picks)
                    device.clearPicks()
                }
            }
            loadedFor = account.username
            // Сохранённое на месте — можно считать итоги и оживлять демо
            vm.checkRecapReady()
            vm.startDemoLife()
        } else {
            vm.reset()
        }
    }
    // Любое изменение — в файл, через полсекунды тишины: при наборе
    // комментария не пишем на диск после каждой буквы
    LaunchedEffect(loadedFor) {
        val login = loadedFor ?: return@LaunchedEffect
        vm.state.collectLatest { s ->
            delay(500)
            val snapshot = vm.snapshot()
            withContext(Dispatchers.IO) { runCatching { store.save(login, snapshot, s.me) } }
        }
    }

    // Открыли приложение по ссылке-приглашению — кто позвал и к чему.
    // Только когда сохранённое уже на месте: иначе у старого аккаунта без
    // записей (до restore) приглашение сочли бы регистрацией по ссылке.
    // Пока не вошли — ссылка ждёт в openedInvite.
    LaunchedEffect(loadedFor) {
        if (loadedFor == null) return@LaunchedEffect
        MainActivity.openedInvite.collect { invite ->
            if (invite != null) {
                vm.openInvite(invite)
                MainActivity.openedInvite.value = null
            }
        }
    }

    // Заставка держится, пока проверяется сохранённая сессия: иначе на
    // долю секунды мелькнул бы экран входа у уже вошедшего человека
    // Документы (соглашение, правила, согласие на обработку) не приняты в
    // текущей версии — сначала они, потом приложение
    val needsConsent = account != null && remember(account.username, auth.consentTick) { !authVm.hasConsent(account.username) }
    val stage = when {
        !splashDone || auth.checking -> Stage.Splash
        account == null && !onboarded -> Stage.Onboarding
        account == null -> Stage.Auth
        needsConsent -> Stage.Consent
        else -> Stage.Main
    }
    Crossfade(targetState = stage, animationSpec = tween(500), label = "stage") { s ->
        when (s) {
            Stage.Splash -> AtomSplash(onFinished = { splashDone = true })
            Stage.Onboarding -> OnboardingScreen(
                onDone = { picks ->
                    device.savePicks(picks)
                    device.setOnboarded()
                    registerNext = true
                    onboarded = true
                },
                onSkip = {
                    device.setOnboarded()
                    onboarded = true
                },
            )
            Stage.Auth -> AuthScreen(authVm, auth, startRegistering = registerNext)
            Stage.Consent -> ConsentGate(onAccept = authVm::acceptDocuments, onLogout = authVm::logout)
            Stage.Main -> MainScreen(
                vm,
                onSaveProfile = authVm::saveProfile,
                account = AccountActions(
                    logout = authVm::logout,
                    changePassword = authVm::changePassword,
                    deleteAccount = { password ->
                        val login = account?.username
                        val error = authVm.deleteAccount(password)
                        if (error == null && login != null) {
                            withContext(Dispatchers.IO) { store.delete(login) }
                        }
                        error
                    },
                ),
            )
        }
    }
}

@Composable
private fun MainScreen(vm: AppViewModel, onSaveProfile: (String, ProfileInfo) -> Unit, account: AccountActions) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Уведомления: приложение открыто — плашкой сверху, свёрнуто — системным
    LaunchedEffect(vm) {
        vm.nudges.collect { nudge ->
            if (AppVisibility.foreground) {
                vm.showBanner(nudge)
                // Плашка звучит тем же звуком, что и пуш этого вида
                SoundPlayer.play(context.applicationContext, SoundPrefs(context.applicationContext).get(nudge.kind))
            } else MemoryNotifications.show(context.applicationContext, nudge)
        }
    }
    // Пуш с сервера, пока приложение открыто, — той же плашкой
    LaunchedEffect(vm) {
        PushBus.incoming.collect { vm.notify(it) }
    }
    // Выключатели уведомлений — и для пушей с сервера (их принимает служба без экрана)
    LaunchedEffect(state.notifications) {
        PushBus.saveSettings(context.applicationContext, state.notifications)
    }
    // Открыли приложение нажатием на уведомление — туда, о чём оно
    LaunchedEffect(vm) {
        MainActivity.openedNudge.collect { nudge ->
            if (nudge != null) {
                vm.openNudge(nudge)
                MainActivity.openedNudge.value = null
            }
        }
    }
    // Android 13+: разрешение на уведомления — один раз, при первом входе
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !MemoryNotifications.permitted(context)) {
            val prefs = context.getSharedPreferences("device", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("askedNotifications", false)) {
                prefs.edit().putBoolean("askedNotifications", true).apply()
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val prefs = remember(context) { DevicePrefs(context.applicationContext) }
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
    }

    // «Назад»: просмотр фото → обсуждение → переписка → страница человека
    // → группа → лента → выход
    val canGoBack = state.viewing != null || state.commentsPostId != null || state.commentsPrismId != null ||
        state.openChatId != null || state.openPersonId != null || state.profileSection != null || state.settingsPage != null ||
        state.creatingPrism ||
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
            ChatScreen(vm, openChat, state.chats.filter { it.id != openChat.id }, blocked = state.isBlocked(openChat.peer.id))
        } else {
        when {
            // Страница человека и группы — поверх вкладки, панель вкладок остаётся
            openPerson != null -> PersonScreen(vm, state, openPerson, padding)
            openGroup != null && state.groupSettingsOpen -> GroupSettingsScreen(vm, openGroup, padding)
            openGroup != null -> GroupScreen(vm, state, openGroup, padding)
            else -> AnimatedContent(
                targetState = state.tab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "tab",
            ) { tab ->
                when (tab) {
                    Tab.Feed -> FeedScreen(vm, state, padding)
                    Tab.Stories -> StoriesScreen(vm, state, padding)
                    Tab.Prism -> PrismScreen(vm, state, padding)
                    Tab.Chats -> ChatsScreen(vm, state, padding)
                    Tab.Groups -> GroupsScreen(vm, state, padding)
                    Tab.Together -> TogetherScreen(vm, state, padding)
                    Tab.Profile -> {
                        val section = state.profileSection
                        val settings = state.settingsPage
                        when {
                            settings != null -> SettingsScreen(vm, state, settings, padding, version, prefs, account)
                            section == ProfileSection.Music -> MusicScreen(vm, state, padding)
                            section == ProfileSection.Stickers -> StickerStudioScreen(vm, state, padding)
                            section == ProfileSection.Recap -> RecapScreen(vm, state, padding)
                            section == ProfileSection.Shelf -> ShelfScreen(vm, state, padding)
                            section != null -> ProfileSectionScreen(vm, state, section, padding)
                            else -> ProfileScreen(vm, state, padding, onSaveProfile)
                        }
                    }
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

        // Обсуждение — шторкой поверх любого экрана
        state.commentsPostId?.let { id ->
            state.findPost(id)?.let { post ->
                CommentsSheet(
                    total = post.comments,
                    comments = post.commentList.filter { it.author.id !in state.blocked && it.id !in state.reportedComments },
                    myId = state.me.id,
                    onSend = { text, replyTo, sticker -> vm.addComment(id, text, replyTo, sticker) },
                    onDelete = { vm.deleteComment(id, it) },
                    onReport = { cid -> post.commentList.firstOrNull { it.id == cid }?.let(vm::reportComment) },
                    onLike = { vm.toggleCommentLike(id, it) },
                    onAuthor = { userId -> vm.closeComments(); vm.openPerson(userId) },
                    onDismiss = vm::closeComments,
                    stickers = state.stickers,
                )
            }
        }
        state.commentsPrismId?.let { id ->
            state.findPrism(id)?.let { prism ->
                CommentsSheet(
                    total = prism.comments,
                    comments = prism.commentList.filter { it.author.id !in state.blocked && it.id !in state.reportedComments },
                    myId = state.me.id,
                    onSend = { text, replyTo, sticker -> vm.addPrismComment(id, text, replyTo, sticker) },
                    onDelete = { vm.deletePrismComment(id, it) },
                    onReport = { cid -> prism.commentList.firstOrNull { it.id == cid }?.let(vm::reportComment) },
                    onLike = { vm.togglePrismCommentLike(id, it) },
                    onAuthor = { userId -> vm.closeComments(); vm.openPerson(userId) },
                    onDismiss = vm::closeComments,
                    stickers = state.stickers,
                )
            }
        }
        state.banner?.let { nudge ->
            NudgeBanner(
                nudge,
                onOpen = { vm.openNudge(nudge) },
                onDismiss = vm::dismissBanner,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        state.echo?.let { EchoDialog(vm, state, it) }
        state.openedInvite?.let { InviteDialog(vm, it) }
        state.viewing?.let { MediaViewer(it, onClose = vm::closeMedia) }
        state.reporting?.let { target ->
            ReportDialog(
                target,
                onSend = { reason, note, block ->
                    vm.report(target, reason, note, block)
                    Toast.makeText(context, t("Спасибо. Жалоба ушла модераторам, у вас это скрыто."), Toast.LENGTH_LONG).show()
                },
                onDismiss = vm::cancelReport,
                onRules = { vm.openLegal(LegalDoc.Community) },
            )
        }
        state.legalDoc?.let { LegalDocViewer(it, onClose = vm::closeLegal) }
        // Свой PRISM — камера и редактор во весь экран, поверх всего
        if (state.creatingPrism) PrismCreateFlow(vm, state)
    }
}

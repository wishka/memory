package ru.wishka.memory.ui.components

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.InviteAction
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.ShareCard
import ru.wishka.memory.data.ShareCardSpec
import ru.wishka.memory.ui.AppViewModel

/**
 * Поделиться карточкой-картинкой: рисуем её не в главном потоке, кладём
 * во вложения и открываем системное «Поделиться» — с текстом-приглашением
 * и ссылкой, привязанной к действию. В журнал роста — что отправили.
 */
fun shareCard(
    context: Context,
    scope: CoroutineScope,
    vm: AppViewModel,
    spec: ShareCardSpec,
    name: String,
    action: InviteAction,
    targetId: String = "",
    title: String = "",
) {
    scope.launch {
        val image = withContext(Dispatchers.Default) { ShareCard.save(context, spec.copy(inviteCode = vm.state.value.inviteCode), name) }
        val text = vm.invite(action, targetId, title)
        vm.logCardShared(name)
        MediaFiles.share(context, text, listOfNotNull(image))
    }
}

/** Позвать того, кого ещё нет в Memory: только текст со ссылкой. */
fun shareInvite(context: Context, vm: AppViewModel, action: InviteAction, targetId: String = "", title: String = "") {
    MediaFiles.share(context, vm.invite(action, targetId, title), emptyList())
}

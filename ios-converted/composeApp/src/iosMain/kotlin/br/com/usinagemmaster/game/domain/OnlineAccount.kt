@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.usinagemmaster.game.domain

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults

private const val ACCOUNT_LABEL_KEY = "usinagemmaster.online.account.label"
private const val OPEN_ACCOUNT_NOTIFICATION = "UsinagemOpenOnlineAccount"

actual fun onlineAccountLabel(): String =
    NSUserDefaults.standardUserDefaults.stringForKey(ACCOUNT_LABEL_KEY)
        ?: "Offline • progresso local"

actual fun openOnlineAccountPanel() {
    NSNotificationCenter.defaultCenter.postNotificationName(
        OPEN_ACCOUNT_NOTIFICATION,
        null,
    )
}

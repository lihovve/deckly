package app.deckly

import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Экран «Доступ ко всем файлам» именно для этого приложения. */
fun buildFileAccessSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", packageName, null))

/** Общий список приложений с таким доступом: запасной путь, если экрана приложения на прошивке нет. */
fun buildCommonFileAccessSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

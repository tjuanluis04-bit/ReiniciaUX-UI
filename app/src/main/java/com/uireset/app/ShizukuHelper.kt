package com.uireset.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_RUNNING, NO_PERMISSION, READY }

enum class RestartAction(val title: String, val subtitle: String) {
    SYSTEM_UI("Reiniciar SystemUI", "Barra de estado, panel rápido y notificaciones"),
    LAUNCHER("Reiniciar launcher", "Pantalla de inicio y cajón de apps"),
    BOTH("Reiniciar todo", "Launcher y SystemUI en un solo paso")
}

object ShizukuHelper {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val SYSTEM_UI = "com.android.systemui"
    private const val REQUEST_CODE = 1001

    fun state(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
            Shizuku.isPreV11() -> ShizukuState.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.NO_PERMISSION
            else -> ShizukuState.READY
        }
    } catch (e: Throwable) {
        ShizukuState.NOT_RUNNING
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) {
        }
    }

    fun shizukuLaunchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)

    private fun launcherPackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pkg = context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        return pkg?.takeIf { it != "android" && it != context.packageName }
    }

    private fun commands(context: Context, action: RestartAction): List<String> {
        val ui = "am crash $SYSTEM_UI || am force-stop $SYSTEM_UI"
        val launcher = launcherPackage(context)?.let { "am force-stop $it" }
        return when (action) {
            RestartAction.SYSTEM_UI -> listOf(ui)
            RestartAction.LAUNCHER -> listOf(launcher ?: error("No se encontró el launcher"))
            RestartAction.BOTH -> listOfNotNull(launcher, ui)
        }
    }

    private suspend fun waitForBinder(timeoutMs: Long): Boolean {
        var waited = 0L
        while (!Shizuku.pingBinder() && waited < timeoutMs) {
            delay(100)
            waited += 100
        }
        return Shizuku.pingBinder()
    }

    // Shizuku.newProcess es privado desde la versión 13.1; se invoca por reflexión.
    private fun shell(cmd: String) {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val err = process.errorStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) error(err.trim().ifBlank { "código de salida $code" })
    }

    suspend fun execute(context: Context, action: RestartAction): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!waitForBinder(2500)) error("Shizuku no está activo")
                if (state() != ShizukuState.READY) error("Falta el permiso de Shizuku")
                commands(context, action).forEach { shell(it) }
            }
        }
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.uireset.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { UiResetTheme { HomeScreen() } }
    }
}

@Composable
fun UiResetTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var state by remember { mutableStateOf(ShizukuHelper.state()) }
    var running by remember { mutableStateOf<RestartAction?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = ShizukuHelper.state() }

    DisposableEffect(Unit) {
        val refresh = { state = ShizukuHelper.state() }
        val received = Shizuku.OnBinderReceivedListener { refresh() }
        val dead = Shizuku.OnBinderDeadListener { refresh() }
        val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }

    fun restart(action: RestartAction) {
        if (running != null) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            running = action
            val result = ShizukuHelper.execute(context, action)
            running = null
            snackbar.showSnackbar(
                result.fold(
                    onSuccess = { "Listo: ${action.title.lowercase()}" },
                    onFailure = { "No se pudo reiniciar: ${it.message}" }
                )
            )
        }
    }

    val ready = state == ShizukuState.READY

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Header(state)
            Spacer(Modifier.height(28.dp))

            HeroButton(
                enabled = ready,
                running = running == RestartAction.SYSTEM_UI,
                onClick = { restart(RestartAction.SYSTEM_UI) }
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    running != null -> "Reiniciando…"
                    ready -> "Toca para reiniciar SystemUI"
                    else -> "Conecta Shizuku para empezar"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(28.dp))

            if (!ready) {
                SetupCard(
                    state = state,
                    onGrant = { ShizukuHelper.requestPermission() },
                    onOpenShizuku = {
                        val intent = ShizukuHelper.shizukuLaunchIntent(context)
                        if (intent != null) context.startActivity(intent)
                        else Toast.makeText(context, "Shizuku no está instalado", Toast.LENGTH_LONG).show()
                    }
                )
                Spacer(Modifier.height(16.dp))
            }

            ActionCard(Icons.Rounded.Home, RestartAction.LAUNCHER, ready && running == null) {
                restart(RestartAction.LAUNCHER)
            }
            Spacer(Modifier.height(12.dp))
            ActionCard(Icons.Rounded.Settings, RestartAction.BOTH, ready && running == null) {
                restart(RestartAction.BOTH)
            }
            Spacer(Modifier.height(24.dp))

            WidgetCard {
                val ok = pinWidget(context)
                if (!ok) {
                    scope.launch {
                        snackbar.showSnackbar("Mantén pulsada la pantalla de inicio y elige Widgets › Reinicio UI")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(state: ShizukuState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Reinicio UI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Reinicia la interfaz sin reiniciar el teléfono",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        StatusChip(state)
    }
}

@Composable
private fun StatusChip(state: ShizukuState) {
    val scheme = MaterialTheme.colorScheme
    val (label, container, content) = when (state) {
        ShizukuState.READY -> Triple("Conectado", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        ShizukuState.NO_PERMISSION -> Triple("Sin permiso", scheme.secondaryContainer, scheme.onSecondaryContainer)
        ShizukuState.NOT_RUNNING -> Triple("Inactivo", scheme.errorContainer, scheme.onErrorContainer)
    }
    Surface(shape = CircleShape, color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(content)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

@Composable
private fun HeroButton(enabled: Boolean, running: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "giro")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "angulo"
    )
    val scale by animateFloatAsState(if (running) 0.94f else 1f, label = "escala")
    val scheme = MaterialTheme.colorScheme
    val container = if (enabled) scheme.primary else scheme.surfaceVariant
    val content = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(224.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .scale(scale)
                .clip(CircleShape)
                .background(container.copy(alpha = 0.14f))
        )
        Box(
            Modifier
                .size(192.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(container.copy(alpha = 0.22f))
        )
        Surface(
            onClick = onClick,
            enabled = enabled && !running,
            shape = CircleShape,
            color = container,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(160.dp)
                .scale(scale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Reiniciar SystemUI",
                    tint = content,
                    modifier = Modifier
                        .size(72.dp)
                        .rotate(if (running) angle else 0f)
                )
            }
        }
    }
}

@Composable
private fun ActionCard(icon: ImageVector, action: RestartAction, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(action.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    action.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SetupCard(state: ShizukuState, onGrant: () -> Unit, onOpenShizuku: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            val noPermission = state == ShizukuState.NO_PERMISSION
            Text(
                if (noPermission) "Concede el permiso a Shizuku" else "Inicia Shizuku",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (noPermission)
                    "La app necesita el permiso para ejecutar el reinicio. Si el diálogo no aparece, actívala en Shizuku › Aplicaciones autorizadas."
                else
                    "Abre Shizuku y arranca el servicio (con depuración inalámbrica o root). Después vuelve aquí.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(14.dp))
            if (noPermission) {
                Button(onClick = onGrant) { Text("Conceder permiso") }
            } else {
                Button(onClick = onOpenShizuku) { Text("Abrir Shizuku") }
            }
        }
    }
}

@Composable
private fun WidgetCard(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Widget en la pantalla de inicio",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Reinicia SystemUI con un toque, sin abrir la app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Añadir widget")
        }
    }
}

private fun pinWidget(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    return if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, UiRestartWidget::class.java), null, null)
    } else false
}

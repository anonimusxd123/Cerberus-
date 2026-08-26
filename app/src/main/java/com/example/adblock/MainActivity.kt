package com.example.adblock

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.adblock.filtering.BlocklistManager
import com.example.adblock.settings.SettingsManager
import com.example.adblock.statistics.StatisticsManager
import com.example.adblock.vpn.AdBlockVpnService
import java.text.DateFormat

private val Background = Color(0xFF080A0F)
private val Panel = Color(0xFF10131B)
private val PanelBorder = Color(0xFF292D38)
private val Green = Color(0xFF00D66B)
private val Muted = Color(0xFF8B91A0)

class MainActivity : ComponentActivity() {
    private val vpnRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> if (result.resultCode == Activity.RESULT_OK) startProtection() }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { CerberusApp(::toggle) } }
    private fun toggle(active: Boolean) { if (active) startService(Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_STOP)) else VpnService.prepare(this)?.let(vpnRequest::launch) ?: startProtection() }
    private fun startProtection() = ContextCompat.startForegroundService(this, Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_START))
}

@Composable private fun CerberusApp(toggle: (Boolean) -> Unit) {
    var screen by remember { mutableStateOf("Inicio") }
    MaterialTheme(colorScheme = darkColorScheme(primary = Green, surface = Panel, background = Background, onBackground = Color.White, onSurface = Color.White)) {
        Scaffold(containerColor = Background, bottomBar = { BottomNav(screen) { screen = it } }) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { when (screen) { "Inicio" -> Dashboard(toggle); "Estadísticas" -> StatisticsScreen(); "Aplicaciones" -> AppsScreen(); else -> SettingsScreen() } } }
    }
}

@Composable private fun BottomNav(selected: String, change: (String) -> Unit) {
    NavigationBar(containerColor = Color(0xFF0D1017), contentColor = Muted) {
        listOf("Inicio" to Icons.Default.Shield, "Estadísticas" to Icons.Default.BarChart, "Aplicaciones" to Icons.Default.Apps, "Ajustes" to Icons.Default.Settings).forEach { (label, icon) ->
            NavigationBarItem(selected = selected == label, onClick = { change(label) }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF123321), unselectedIconColor = Muted, unselectedTextColor = Muted))
        }
    }
}

@Composable private fun Dashboard(toggle: (Boolean) -> Unit) {
    val active by AdBlockVpnService.isActive.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val statsManager = remember(context) { StatisticsManager(context) }
    val stats by statsManager.state.collectAsStateWithLifecycle()
    val stateColor = if (active) Green else Color(0xFFFF5B6E)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF123321)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Shield, null, tint = Green) }
                Spacer(Modifier.width(10.dp)); Column { Text("CERBERUS", letterSpacing = 2.sp, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp); Text("BLOQUEADOR DE ANUNCIOS", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp) }
                Spacer(Modifier.weight(1f)); StatusPill(active)
            }
            Spacer(Modifier.height(18.dp))
            Surface(color = Panel, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0C2518)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Shield, null, tint = Green, modifier = Modifier.size(31.dp)) }
                    Spacer(Modifier.height(14.dp)); Text("ESTADO", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(if (active) "Protección activa" else "Protección desactivada", color = stateColor, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(if (active) "Filtro DNS local en ejecución" else "Pulsa para activar el filtro DNS", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(18.dp)); PowerButton(active) { toggle(active) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard(Modifier.weight(1f), "🚫", stats.adsBlocked, "Anuncios"); MetricCard(Modifier.weight(1f), "🕵️", stats.trackersBlocked, "Trackers"); MetricCard(Modifier.weight(1f), "🌐", stats.allowed, "Solicitudes") }
            Spacer(Modifier.height(16.dp))
        }
        item { ControlPanel() }
    }
}

@Composable private fun StatusPill(active: Boolean) { val color = if (active) Green else Muted; Row(Modifier.clip(CircleShape).background(color.copy(alpha = .13f)).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(5.dp)); Text(if (active) "ACTIVO" else "INACTIVO", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold) } }
@Composable private fun PowerButton(active: Boolean, click: () -> Unit) { val color = if (active) Green else Muted; Box(Modifier.size(68.dp).clip(CircleShape).border(2.dp, color, CircleShape).clickable(onClick = click), contentAlignment = Alignment.Center) { Icon(Icons.Default.PowerSettingsNew, if (active) "Desactivar protección" else "Activar protección", tint = color, modifier = Modifier.size(34.dp)) } }
@Composable private fun MetricCard(modifier: Modifier, emoji: String, amount: Long, label: String) { Surface(modifier, color = Panel, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder)) { Column(Modifier.padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(emoji, fontSize = 18.sp); Spacer(Modifier.height(7.dp)); Text(amount.toString(), color = Green, fontWeight = FontWeight.Bold, fontSize = 19.sp); Text(label, color = Muted, fontSize = 9.sp) } } }

@Composable private fun ControlPanel() {
    val context = androidx.compose.ui.platform.LocalContext.current; val manager = remember(context) { SettingsManager(context) }; val settings by manager.state.collectAsStateWithLifecycle()
    Surface(color = Panel, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder), modifier = Modifier.fillMaxWidth()) { Column { DashboardToggle("Bloquear anuncios", "Elimina dominios publicitarios", Icons.Default.Block, settings.blockAds) { manager.set { current -> current.copy(blockAds = it) } }; HorizontalDivider(color = PanelBorder); DashboardToggle("Bloquear trackers", "Reduce el rastreo entre sitios", Icons.Default.VisibilityOff, settings.blockTrackers) { manager.set { current -> current.copy(blockTrackers = it) } } } }
}
@Composable private fun DashboardToggle(title: String, detail: String, icon: ImageVector, value: Boolean, change: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Green); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(detail, color = Muted, fontSize = 10.sp) }; Switch(value, change, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Green)) } }

@Composable private fun StatisticsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current; val manager = remember(context) { StatisticsManager(context) }; val stats by manager.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp)) { ScreenTitle("Estadísticas", "Actividad almacenada solo en este dispositivo"); Spacer(Modifier.height(20.dp)); LargeStatistic("Dominios bloqueados", stats.adsBlocked, Icons.Default.Block); LargeStatistic("Trackers bloqueados", stats.trackersBlocked, Icons.Default.VisibilityOff); LargeStatistic("Solicitudes permitidas", stats.allowed, Icons.Default.Public); Spacer(Modifier.weight(1f)); OutlinedButton(onClick = manager::reset, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)) { Text("RESTABLECER ESTADÍSTICAS") } }
}
@Composable private fun LargeStatistic(label: String, value: Long, icon: ImageVector) { Surface(Modifier.fillMaxWidth().padding(vertical = 5.dp), color = Panel, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Green); Spacer(Modifier.width(16.dp)); Column { Text(value.toString(), color = Green, fontWeight = FontWeight.Bold, fontSize = 24.sp); Text(label, color = Muted, fontSize = 12.sp) } } } }

@Composable private fun AppsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val active by AdBlockVpnService.isActive.collectAsStateWithLifecycle()
    val apps = remember { context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.loadLabel(context.packageManager).toString() }.distinct().sorted() }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp)) { item { ScreenTitle("Aplicaciones", "El filtro DNS se aplica a apps que usan el DNS del sistema"); Spacer(Modifier.height(14.dp)) }; items(apps) { app -> Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp), color = Panel, shape = RoundedCornerShape(10.dp)) { ListItem(headlineContent = { Text(app) }, supportingContent = { Text("Protección DNS", color = Muted) }, leadingContent = { Icon(Icons.Default.Android, null, tint = Green) }, trailingContent = { StatusPill(active) }) } } }
}

@Composable private fun SettingsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current; val blocklist = remember(context) { BlocklistManager(context) }; var white by remember { mutableStateOf("") }; var black by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp)) { item { ScreenTitle("Ajustes", "Reglas locales del filtro DNS"); Spacer(Modifier.height(20.dp)); Text("LISTA BLANCA", color = Green, fontSize = 10.sp, letterSpacing = 1.sp); DomainInput(white, { white = it }, "Dominio que nunca se bloqueará") { if (blocklist.addAllowed(white)) white = "" }; Spacer(Modifier.height(18.dp)); Text("LISTA NEGRA", color = Green, fontSize = 10.sp, letterSpacing = 1.sp); DomainInput(black, { black = it }, "Dominio a bloquear") { if (blocklist.addBlocked(black)) black = "" }; Spacer(Modifier.height(22.dp)); Text("Motor: DNS UDP IPv4", color = Muted, fontSize = 11.sp); Text("Última actualización: ${if (blocklist.lastUpdated() == 0L) "sin cambios manuales" else DateFormat.getDateTimeInstance().format(blocklist.lastUpdated())}", color = Muted, fontSize = 11.sp); Text("Versión 0.1.0", color = Muted, fontSize = 11.sp) } }
}
@Composable private fun ScreenTitle(title: String, subtitle: String) { Text(title.uppercase(), letterSpacing = 1.sp, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = Muted, fontSize = 12.sp) }
@Composable private fun DomainInput(value: String, changed: (String) -> Unit, hint: String, submit: () -> Unit) { Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value, changed, Modifier.weight(1f), label = { Text(hint) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green, focusedLabelColor = Green)); Spacer(Modifier.width(8.dp)); IconButton(submit, modifier = Modifier.background(Color(0xFF123321), CircleShape)) { Icon(Icons.Default.Add, "Añadir", tint = Green) } } }

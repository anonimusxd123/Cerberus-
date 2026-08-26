package com.example.adblock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import java.text.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.adblock.filtering.BlocklistManager
import com.example.adblock.settings.SettingsManager
import com.example.adblock.statistics.StatisticsManager
import com.example.adblock.vpn.AdBlockVpnService

class MainActivity : ComponentActivity() {
    private val vpnRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> if (result.resultCode == Activity.RESULT_OK) startProtection() }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AdBlockApp(::toggle) } }
    private fun toggle(active: Boolean) { if (active) startService(Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_STOP)) else VpnService.prepare(this)?.let(vpnRequest::launch) ?: startProtection() }
    private fun startProtection() = ContextCompat.startForegroundService(this, Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_START))
}

@Composable private fun AdBlockApp(toggle: (Boolean) -> Unit) {
    var page by remember { mutableStateOf("Inicio") }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF146C43))) {
        Scaffold(bottomBar = { NavigationBar { listOf("Inicio" to Icons.Default.Shield, "Estadísticas" to Icons.Default.BarChart, "Aplicaciones" to Icons.Default.Apps, "Configuración" to Icons.Default.Settings).forEach { (name, icon) -> NavigationBarItem(selected = page == name, onClick = { page = name }, icon = { Icon(icon, name) }, label = { Text(name) }) } } }) { padding ->
            Box(Modifier.padding(padding)) { when (page) { "Inicio" -> Home(toggle); "Estadísticas" -> Statistics(); "Aplicaciones" -> Apps(); else -> Settings() } }
        }
    }
}

@Composable private fun Home(toggle: (Boolean) -> Unit) {
    val active by AdBlockVpnService.isActive.collectAsStateWithLifecycle()
    val stats by StatisticsManager(androidx.compose.ui.platform.LocalContext.current).state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Shield, null, Modifier.size(88.dp), tint = if (active) Color(0xFF146C43) else MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(18.dp)); Text("AdBlock Android", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(if (active) "Protección DNS activa" else "Protección desactivada", color = if (active) Color(0xFF146C43) else MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(28.dp)); Text(stats.adsBlocked.toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold); Text("dominios bloqueados")
        Spacer(Modifier.height(28.dp)); Button(onClick = { toggle(active) }, modifier = Modifier.fillMaxWidth()) { Text(if (active) "DESACTIVAR" else "ACTIVAR PROTECCIÓN") }
        Spacer(Modifier.height(26.dp)); Text("DNS local · Sin servidor VPN propio", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable private fun Statistics() {
    val manager = remember { StatisticsManager(androidx.compose.ui.platform.LocalContext.current) }; val stats by manager.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp)) { Text("Estadísticas de hoy", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(24.dp)); Stat("🚫", stats.adsBlocked, "Dominios bloqueados"); Stat("🕵️", stats.trackersBlocked, "Trackers bloqueados"); Stat("🌐", stats.allowed, "Solicitudes permitidas"); Spacer(Modifier.weight(1f)); OutlinedButton(onClick = manager::reset, modifier = Modifier.fillMaxWidth()) { Text("Restablecer estadísticas") } }
}
@Composable private fun Stat(icon: String, value: Long, label: String) { Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(icon, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.width(18.dp)); Column { Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(label) } } }

@Composable private fun Apps() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val apps = remember { context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.loadLabel(context.packageManager).toString() }.distinct().sorted() }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp)) { item { Text("Aplicaciones", style = MaterialTheme.typography.headlineSmall); Text("La primera versión aplica el DNS del sistema; las excepciones por aplicación aún no están implementadas.", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(12.dp)) }; items(apps) { app -> ListItem(headlineContent = { Text(app) }, leadingContent = { Icon(Icons.Default.Android, null) }, trailingContent = { Text("DNS") }); HorizontalDivider() } }
}

@Composable private fun Settings() {
    val context = androidx.compose.ui.platform.LocalContext.current; val manager = remember { SettingsManager(context) }; val settings by manager.state.collectAsStateWithLifecycle(); val blocklist = remember { BlocklistManager(context) }
    var white by remember { mutableStateOf("") }; var black by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp)) { item { Text("Configuración", style = MaterialTheme.typography.headlineSmall); Toggle("Bloquear anuncios", settings.blockAds) { manager.set { s -> s.copy(blockAds = it) } }; Toggle("Bloquear trackers", settings.blockTrackers) { manager.set { s -> s.copy(blockTrackers = it) } }; Toggle("Iniciar al arrancar (próximamente)", false, enabled = false) {}; Toggle("Actualizar listas automáticamente (sin fuentes aún)", false, enabled = false) {}; Spacer(Modifier.height(16.dp)); Text("Lista blanca", fontWeight = FontWeight.Bold); DomainInput(white, { white = it }, "Dominio que nunca se bloqueará") { if (blocklist.addAllowed(white)) white = "" }; Spacer(Modifier.height(12.dp)); Text("Lista negra", fontWeight = FontWeight.Bold); DomainInput(black, { black = it }, "Dominio a bloquear") { if (blocklist.addBlocked(black)) black = "" }; Spacer(Modifier.height(20.dp)); Text("Estado del motor: DNS UDP IPv4", style = MaterialTheme.typography.bodySmall); Text("Última actualización: ${if (blocklist.lastUpdated() == 0L) "sin cambios manuales" else DateFormat.getDateTimeInstance().format(blocklist.lastUpdated())}", style = MaterialTheme.typography.bodySmall); Text("Versión 0.1.0", style = MaterialTheme.typography.bodySmall) } }
}
@Composable private fun Toggle(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onCheckedChange = onChange, enabled = enabled) }
@Composable private fun DomainInput(value: String, changed: (String) -> Unit, hint: String, submit: () -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value, changed, Modifier.weight(1f), label = { Text(hint) }, singleLine = true); Spacer(Modifier.width(8.dp)); IconButton(submit) { Icon(Icons.Default.Add, "Añadir") } }

package com.archy.arcmeshcomm.ui

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.archy.arcmeshcomm.ble.BleMeshController
import com.archy.arcmeshcomm.mesh.MeshEngine
import com.archy.arcmeshcomm.models.DeliveryStatus
import com.archy.arcmeshcomm.models.EventSeverity
import com.archy.arcmeshcomm.models.MeshMessage
import com.archy.arcmeshcomm.models.MeshNode
import com.archy.arcmeshcomm.models.MeshPacket
import com.archy.arcmeshcomm.models.MeshUiState
import com.archy.arcmeshcomm.models.MessageDirection
import com.archy.arcmeshcomm.models.NetworkEvent
import com.archy.arcmeshcomm.models.NodeStatus
import com.archy.arcmeshcomm.service.MeshForegroundService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ArcRoute(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Home", Icons.Default.Home),
    Chat("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
    Nodes("nodes", "Nodes", Icons.Default.People),
    Packets("packets", "Packets", Icons.Default.Storage),
    Guide("guide", "Guide", Icons.Default.Info)
}

@Composable
fun ArcMeshApp() {
    val context = LocalContext.current
    val engine = remember { MeshEngine.get(context) }
    val state by engine.state.collectAsState()
    val navController = rememberNavController()

    Scaffold(
        topBar = { ArcTopBar(state = state, engine = engine) },
        bottomBar = { ArcBottomBar(navController = navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ArcRoute.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(ArcRoute.Dashboard.route) {
                DashboardScreen(state = state, engine = engine, navController = navController)
            }
            composable(ArcRoute.Chat.route) {
                ChatScreen(state = state, engine = engine)
            }
            composable(ArcRoute.Nodes.route) {
                NodesScreen(state = state, engine = engine)
            }
            composable(ArcRoute.Packets.route) {
                PacketsScreen(state = state, engine = engine)
            }
            composable(ArcRoute.Guide.route) {
                GuideScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArcTopBar(state: MeshUiState, engine: MeshEngine) {
    val context = LocalContext.current
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ArcMeshComm", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (state.radioEnabled) "secure offline mesh active" else "radio paused",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = { engine.toggleRadio() }) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Toggle radio")
            }
        },
        actions = {
            IconButton(
                onClick = {
                    val serviceIntent = Intent(context, MeshForegroundService::class.java)
                    if (state.serviceRunning) {
                        context.stopService(serviceIntent)
                        engine.setServiceRunning(false)
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(context, serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        engine.setServiceRunning(true)
                    }
                }
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = "Toggle background service")
            }
        }
    )
}

@Composable
private fun ArcBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    NavigationBar {
        ArcRoute.entries.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    state: MeshUiState,
    engine: MeshEngine,
    navController: NavHostController
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Encrypted mesh console",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Send AES-GCM packets, track relay hops, queue offline messages, and monitor nearby nodes without a server.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { navController.navigate(ArcRoute.Chat.route) }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Message")
                        }
                        OutlinedButton(onClick = { engine.simulateIncoming() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Simulate RX")
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile("Peers", state.nodes.size.toString(), Modifier.weight(1f))
                MetricTile("Packets", state.packets.size.toString(), Modifier.weight(1f))
                MetricTile("Queued", state.messages.count { it.status == DeliveryStatus.QUEUED }.toString(), Modifier.weight(1f))
            }
        }
        item {
            SectionTitle("Network Events")
        }
        items(state.events.take(8), key = { it.id }) { event ->
            EventRow(event)
        }
    }
}

@Composable
private fun ChatScreen(state: MeshUiState, engine: MeshEngine) {
    var draft by remember { mutableStateOf("") }
    val peer = state.nodes.firstOrNull { it.id == state.selectedPeerId }
    val messages = state.messages.filter {
        it.peerId == state.selectedPeerId || it.direction == MessageDirection.SYSTEM
    }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.nodes, key = { it.id }) { node ->
                FilterChip(
                    selected = node.id == state.selectedPeerId,
                    onClick = { engine.selectPeer(node.id) },
                    label = { Text(node.callsign) },
                    leadingIcon = { StatusDot(node.status) }
                )
            }
        }
        if (peer != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${peer.role} - ${peer.hops} hop path - RSSI ${peer.rssi} dBm",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message)
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Encrypted message") },
                minLines = 1,
                maxLines = 3
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    engine.sendMessage(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank() && state.radioEnabled
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun NodesScreen(state: MeshUiState, engine: MeshEngine) {
    val context = LocalContext.current
    val controller = remember { BleMeshController(context) }
    var readiness by remember { mutableStateOf(controller.readiness()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        readiness = controller.readiness()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (readiness.ready) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (readiness.ready) "BLE transport ready" else "BLE transport needs attention",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        bleStatusText(readiness.supported, readiness.enabled, readiness.permissionsGranted),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            launcher.launch(BleMeshController.requiredPermissions().toTypedArray())
                        }) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Permissions")
                        }
                        OutlinedButton(onClick = {
                            engine.discoverNode()
                            readiness = controller.readiness()
                        }) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Discover")
                        }
                    }
                }
            }
        }
        item { SectionTitle("Known Nodes") }
        items(state.nodes, key = { it.id }) { node ->
            NodeRow(node = node, selected = node.id == state.selectedPeerId) {
                engine.selectPeer(node.id)
            }
        }
    }
}

@Composable
private fun PacketsScreen(state: MeshUiState, engine: MeshEngine) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ElevatedButton(onClick = { engine.retryQueuedPackets() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Flush Queue")
                }
                OutlinedButton(onClick = { engine.clearHistory() }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear")
                }
            }
        }
        item { SectionTitle("Packet Log") }
        if (state.packets.isEmpty()) {
            item {
                EmptyState("No packets yet. Send a message or simulate an inbound relay.")
            }
        } else {
            items(state.packets, key = { it.id }) { packet ->
                PacketRow(packet)
            }
        }
    }
}

@Composable
private fun GuideScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { GuideBlock("1. Start", "Grant BLE permissions on the Nodes screen, then keep the foreground service enabled when you need background monitoring.") }
        item { GuideBlock("2. Discover", "Use Discover to add nearby peers in this prototype. Real BLE readiness is shown separately from the simulated mesh engine.") }
        item { GuideBlock("3. Message", "Choose a peer in Chat. Messages are encrypted with AES-GCM, wrapped in packets, assigned a TTL, and stored locally.") }
        item { GuideBlock("4. Relay", "Offline destinations are queued. Flush Queue simulates store-and-forward delivery when a relay path returns.") }
        item { GuideBlock("5. Inspect", "Open Packets to review nonce, checksum, hop count, TTL, and delivery state for each packet.") }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageBubble(message: MeshMessage) {
    val isOutbound = message.direction == MessageDirection.OUTBOUND
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutbound) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when (message.direction) {
                MessageDirection.OUTBOUND -> MaterialTheme.colorScheme.primaryContainer
                MessageDirection.INBOUND -> MaterialTheme.colorScheme.secondaryContainer
                MessageDirection.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth(0.86f)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.senderName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    AssistChip(onClick = {}, label = { Text(message.status.name.lowercase()) })
                }
                Text(message.body, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "route ${message.route.joinToString(" -> ")} | cipher ${message.encryptedPreview}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NodeRow(node: MeshNode, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(node.status)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(node.callsign, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(node.role, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${node.hops} hops", style = MaterialTheme.typography.labelLarge)
                Text("${node.batteryPercent}% | ${node.rssi} dBm", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PacketRow(packet: MeshPacket) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(packet.status.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(time(packet.timestamp), style = MaterialTheme.typography.labelMedium)
            }
            Text("${packet.senderId} -> ${packet.receiverId}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "ttl ${packet.ttl} | hops ${packet.hopCount} | checksum ${packet.checksum}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "nonce ${packet.nonce.take(18)}... | cipher ${packet.cipherText.take(36)}...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EventRow(event: NetworkEvent) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(severityColor(event.severity))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, fontWeight = FontWeight.Bold)
                Text(event.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(time(event.timestamp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun GuideBlock(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun StatusDot(status: NodeStatus) {
    val color = when (status) {
        NodeStatus.ONLINE -> Color(0xFF1B7F4C)
        NodeStatus.RELAY -> Color(0xFFB26A00)
        NodeStatus.OFFLINE -> Color(0xFF9E2F35)
    }
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

private fun severityColor(severity: EventSeverity): Color {
    return when (severity) {
        EventSeverity.INFO -> Color(0xFF3F6FB5)
        EventSeverity.SUCCESS -> Color(0xFF1B7F4C)
        EventSeverity.WARNING -> Color(0xFFB26A00)
        EventSeverity.ERROR -> Color(0xFF9E2F35)
    }
}

private fun bleStatusText(supported: Boolean, enabled: Boolean, permissions: Boolean): String {
    return when {
        !supported -> "This device does not report Bluetooth LE support."
        !enabled -> "Bluetooth is available but currently disabled in system settings."
        !permissions -> "Nearby-device permissions are required before live BLE scanning or advertising."
        else -> "Hardware, adapter state, and permissions are ready for live BLE integration."
    }
}

private fun time(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

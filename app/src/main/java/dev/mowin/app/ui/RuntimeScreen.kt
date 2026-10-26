package dev.mowin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mowin.app.runtime.DeviceSnapshot
import dev.mowin.app.runtime.ProcessRunner
import dev.mowin.app.runtime.RuntimeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeScreen(snapshot: DeviceSnapshot, state: RuntimeState) {
    val runner = remember { ProcessRunner() }
    var cmd by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("$ mowin — interactive (type + tap Run)\n") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("MoWin", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(snapshot = snapshot, state = state)
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("=== TERMINAL OUTPUT ===", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(out, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0E14)).padding(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = cmd, onValueChange = { cmd = it }, label = { Text("cmd") }, modifier = Modifier.weight(1f), singleLine = true)
                        Button(onClick = {
                            if (cmd.isNotBlank()) {
                                out += "> $cmd\n"
                                val exit = runner.run(argv = listOf("/bin/sh", "-c", cmd), onLine = { line -> out += "$line\n" })
                                out += "exit=$exit\n"
                                cmd = ""
                            }
                        }) { Text("Run") }
                    }
                }
            }
        }
    }
}

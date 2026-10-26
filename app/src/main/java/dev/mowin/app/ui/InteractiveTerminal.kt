package dev.mowin.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.mowin.app.runtime.DeviceSnapshot
import dev.mowin.app.runtime.ProcessRunner
import dev.mowin.app.runtime.RuntimeState

@Composable
fun InteractiveTerminal(
    snapshot: DeviceSnapshot,
    state: RuntimeState,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("$ mowin\nruntime: ${state.label.lowercase()}") }
    val runner = remember { ProcessRunner() }

    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = output,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Divider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("command") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        output += "\n> $input\n"
                        val exit = runner.run(
                            argv = listOf("/bin/sh", "-c", input),
                            cwd = null,
                            onLine = { line -> output += "$line\n" },
                        )
                        output += "exit=$exit\n"
                        input = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Run")
            }
        }
    }
}

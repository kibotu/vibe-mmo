package net.kibotu.ro.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.kibotu.ro.client.util.LogEntry
import net.kibotu.ro.client.util.LogLevel

@Composable
fun Terminal(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    
    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (isExpanded) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    Column(modifier = modifier) {
        // Terminal header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Terminal",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Client Terminal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${logs.size} logs)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                Row {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear logs",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        
        // Terminal content
        if (isExpanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(8.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "No logs yet...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888),
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        logs.forEach { log ->
                            Text(
                                text = log.format(),
                                style = MaterialTheme.typography.bodySmall,
                                color = when (log.level) {
                                    LogLevel.DEBUG -> Color(0xFF888888)
                                    LogLevel.INFO -> Color(0xFFCCCCCC)
                                    LogLevel.WARN -> Color(0xFFFFAA00)
                                    LogLevel.ERROR -> Color(0xFFFF5555)
                                },
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


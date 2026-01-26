package com.pantrywise.ui.shopping

import android.Manifest
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pantrywise.services.VoiceParsedItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun VoiceInputSheet(
    listId: String,
    onDismiss: () -> Unit,
    onItemsAdded: (Int) -> Unit,
    viewModel: VoiceInputViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // Set list ID
    LaunchedEffect(listId) {
        viewModel.setListId(listId)
    }

    // Handle events
    LaunchedEffect(uiState.event) {
        when (val event = uiState.event) {
            is VoiceInputEvent.ItemsAdded -> {
                Toast.makeText(context, "Added ${event.count} items", Toast.LENGTH_SHORT).show()
                onItemsAdded(event.count)
                onDismiss()
            }
            is VoiceInputEvent.Error -> {
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
        if (uiState.event != VoiceInputEvent.None) {
            viewModel.clearEvent()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                "Voice Input",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Say what you need to add to your list",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            when {
                !viewModel.isVoiceAvailable -> {
                    VoiceNotAvailableContent()
                }
                !micPermissionState.status.isGranted -> {
                    PermissionRequestContent(
                        onRequestPermission = { micPermissionState.launchPermissionRequest() }
                    )
                }
                uiState.state == VoiceInputState.RESULTS && uiState.parsedItems.isNotEmpty() -> {
                    ResultsContent(
                        uiState = uiState,
                        onToggleItem = { viewModel.toggleItemSelection(it) },
                        onRemoveItem = { viewModel.removeItem(it) },
                        onSelectAll = { viewModel.selectAll() },
                        onDeselectAll = { viewModel.deselectAll() },
                        onAddItems = { viewModel.addSelectedItemsToList() },
                        onTryAgain = { viewModel.startListening() }
                    )
                }
                else -> {
                    ListeningContent(
                        state = uiState.state,
                        partialText = uiState.partialText,
                        errorMessage = uiState.errorMessage,
                        onStartListening = { viewModel.startListening() },
                        onStopListening = { viewModel.stopListening() }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceNotAvailableContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            Icons.Default.MicOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Voice input not available",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Speech recognition is not supported on this device",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PermissionRequestContent(onRequestPermission: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Microphone Permission Required",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "To use voice input, please allow microphone access",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
private fun ListeningContent(
    state: VoiceInputState,
    partialText: String,
    errorMessage: String?,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Animated microphone button
        val isListening = state == VoiceInputState.LISTENING || state == VoiceInputState.READY

        val pulseAnimation = rememberInfiniteTransition(label = "pulse")
        val scale by pulseAnimation.animateFloat(
            initialValue = 1f,
            targetValue = if (isListening) 1.2f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .scale(if (isListening) scale else 1f)
        ) {
            // Outer pulse ring
            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            CircleShape
                        )
                )
            }

            // Main button
            FilledIconButton(
                onClick = {
                    if (isListening) onStopListening() else onStartListening()
                },
                modifier = Modifier.size(80.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop" else "Start listening",
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Status text
        Text(
            text = when (state) {
                VoiceInputState.IDLE -> "Tap to start"
                VoiceInputState.READY -> "Ready..."
                VoiceInputState.LISTENING -> "Listening..."
                VoiceInputState.PROCESSING -> "Processing..."
                VoiceInputState.ERROR -> errorMessage ?: "Error"
                else -> ""
            },
            style = MaterialTheme.typography.titleMedium,
            color = when (state) {
                VoiceInputState.ERROR -> MaterialTheme.colorScheme.error
                VoiceInputState.LISTENING -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        // Partial recognition text
        AnimatedVisibility(visible = partialText.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = partialText,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Processing indicator
        if (state == VoiceInputState.PROCESSING) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        // Error retry
        if (state == VoiceInputState.ERROR) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onStartListening) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Example phrases
        Text(
            "Try saying:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        val examples = listOf(
            "Two pounds of chicken",
            "Milk and eggs",
            "A dozen bagels and cream cheese",
            "Three cans of tomatoes"
        )

        examples.forEach { example ->
            Text(
                "\"$example\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ResultsContent(
    uiState: VoiceInputUiState,
    onToggleItem: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onAddItems: () -> Unit,
    onTryAgain: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Recognized text
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Heard:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    uiState.recognizedText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selection controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${uiState.selectedItems.size} of ${uiState.parsedItems.size} selected",
                style = MaterialTheme.typography.bodyMedium
            )
            Row {
                TextButton(onClick = onSelectAll) {
                    Text("All")
                }
                TextButton(onClick = onDeselectAll) {
                    Text("None")
                }
            }
        }

        // Items list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(uiState.parsedItems) { index, item ->
                ParsedItemRow(
                    item = item,
                    isSelected = index in uiState.selectedItems,
                    onToggle = { onToggleItem(index) },
                    onRemove = { onRemoveItem(index) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onTryAgain,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Try Again")
            }
            Button(
                onClick = onAddItems,
                modifier = Modifier.weight(1f),
                enabled = uiState.selectedItems.isNotEmpty()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add ${uiState.selectedItems.size}")
            }
        }
    }
}

@Composable
private fun ParsedItemRow(
    item: VoiceParsedItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (!isSelected) TextDecoration.LineThrough else null
                )
                Text(
                    buildString {
                        append(item.quantity.let {
                            if (it == it.toLong().toDouble()) it.toLong() else it
                        })
                        item.unit?.let { append(" $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

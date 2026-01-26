package com.pantrywise.ui.scanner

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pantrywise.data.local.entity.ReceiptEntity
import com.pantrywise.data.local.entity.ReceiptLineItem
import com.pantrywise.data.local.entity.ReceiptStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ReceiptScannerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReceiptScannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onImageSelected(it) }
    }

    // Handle events
    LaunchedEffect(uiState.event) {
        when (val event = uiState.event) {
            is ReceiptScannerEvent.ProcessingComplete -> {
                val confidence = event.result.confidence
                if (confidence < 0.7f) {
                    Toast.makeText(
                        context,
                        "Receipt processed - please review for accuracy",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            is ReceiptScannerEvent.Error -> {
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
            is ReceiptScannerEvent.ReceiptSaved -> {
                Toast.makeText(context, "Receipt saved", Toast.LENGTH_SHORT).show()
            }
            is ReceiptScannerEvent.ReceiptDeleted -> {
                Toast.makeText(context, "Receipt deleted", Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
        if (uiState.event != ReceiptScannerEvent.None) {
            viewModel.clearEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Receipt") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!uiState.isCapturing && uiState.processedReceipt != null) {
                        IconButton(onClick = { viewModel.toggleEditing() }) {
                            Icon(
                                if (uiState.isEditing) Icons.Default.Done else Icons.Default.Edit,
                                contentDescription = if (uiState.isEditing) "Save" else "Edit"
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.toggleRecentReceipts() }) {
                        Icon(Icons.Default.History, contentDescription = "Recent Receipts")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.showRecentReceipts -> {
                    RecentReceiptsList(
                        receipts = uiState.recentReceipts,
                        onReceiptClick = { viewModel.loadReceipt(it) },
                        onClose = { viewModel.toggleRecentReceipts() }
                    )
                }
                uiState.isProcessing -> {
                    ProcessingView()
                }
                uiState.isCapturing -> {
                    if (cameraPermissionState.status.isGranted) {
                        CaptureView(
                            onImageCaptured = { viewModel.onImageCaptured(it) },
                            onGalleryClick = { galleryLauncher.launch("image/*") },
                            isAIConfigured = viewModel.isAIConfigured
                        )
                    } else {
                        PermissionRequestView(
                            onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                        )
                    }
                }
                uiState.processedReceipt != null -> {
                    ReceiptResultView(
                        receipt = uiState.processedReceipt!!,
                        lineItems = uiState.lineItems,
                        rawText = uiState.rawText,
                        imageUri = uiState.capturedImageUri,
                        isEditing = uiState.isEditing,
                        editedStoreName = uiState.editedStoreName,
                        editedTotal = uiState.editedTotal,
                        editedTax = uiState.editedTax,
                        onStoreNameChange = { viewModel.updateStoreName(it) },
                        onTotalChange = { viewModel.updateTotal(it) },
                        onTaxChange = { viewModel.updateTax(it) },
                        onLineItemUpdate = { index, item -> viewModel.updateLineItem(index, item) },
                        onLineItemRemove = { viewModel.removeLineItem(it) },
                        onSaveChanges = { viewModel.saveChanges() },
                        onRetake = { viewModel.retakePhoto() },
                        onDelete = { viewModel.deleteReceipt() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureView(
    onImageCaptured: (Uri) -> Unit,
    onGalleryClick: () -> Unit,
    isAIConfigured: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Column(modifier = Modifier.fillMaxSize()) {
        // AI status indicator
        if (!isAIConfigured) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "AI not configured - basic parsing only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Camera preview
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )

            // Receipt frame overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        )
                )
            }

            // Instructions
            Text(
                text = "Position receipt within frame",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Controls
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button
                IconButton(
                    onClick = onGalleryClick,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Capture button
                Button(
                    onClick = {
                        val photoFile = File(
                            context.cacheDir,
                            "receipt_${System.currentTimeMillis()}.jpg"
                        )

                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        imageCapture?.takePicture(
                            outputOptions,
                            cameraExecutor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    val savedUri = Uri.fromFile(photoFile)
                                    onImageCaptured(savedUri)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    exception.printStackTrace()
                                }
                            }
                        )
                    },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Capture",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Placeholder for symmetry
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun ProcessingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Processing receipt...",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Extracting text and identifying items",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionRequestView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Camera Permission Required",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "To scan receipts, please allow camera access",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
private fun ReceiptResultView(
    receipt: ReceiptEntity,
    lineItems: List<ReceiptLineItem>,
    rawText: String?,
    imageUri: Uri?,
    isEditing: Boolean,
    editedStoreName: String,
    editedTotal: String,
    editedTax: String,
    onStoreNameChange: (String) -> Unit,
    onTotalChange: (String) -> Unit,
    onTaxChange: (String) -> Unit,
    onLineItemUpdate: (Int, ReceiptLineItem) -> Unit,
    onLineItemRemove: (Int) -> Unit,
    onSaveChanges: () -> Unit,
    onRetake: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRawText by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Receipt image thumbnail
        imageUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = "Receipt image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }

        // Status and confidence
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(status = receipt.status)
            Text(
                "Confidence: ${(receipt.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        // Store info
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Store Information",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = editedStoreName,
                    onValueChange = onStoreNameChange,
                    label = { Text("Store Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                Text(
                    receipt.storeName ?: "Unknown Store",
                    style = MaterialTheme.typography.bodyLarge
                )
                receipt.storeAddress?.let { address ->
                    Text(
                        address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider()

        // Financial summary
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editedTax,
                        onValueChange = onTaxChange,
                        label = { Text("Tax") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        prefix = { Text("$") }
                    )
                    OutlinedTextField(
                        value = editedTotal,
                        onValueChange = onTotalChange,
                        label = { Text("Total") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        prefix = { Text("$") }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        receipt.subtotal?.let {
                            Text("Subtotal: ${receipt.formattedSubtotal}")
                        }
                        receipt.tax?.let {
                            Text("Tax: ${receipt.formattedTax}")
                        }
                    }
                    Text(
                        receipt.formattedTotal ?: "Total: Unknown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            receipt.receiptDate?.let { date ->
                Spacer(modifier = Modifier.height(4.dp))
                val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                Text(
                    "Date: ${dateFormat.format(Date(date))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        // Line items
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Items (${lineItems.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (isEditing) {
                    TextButton(onClick = { /* TODO: Add item dialog */ }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Item")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            lineItems.forEachIndexed { index, item ->
                LineItemRow(
                    item = item,
                    isEditing = isEditing,
                    onUpdate = { onLineItemUpdate(index, it) },
                    onRemove = { onLineItemRemove(index) }
                )
            }

            if (lineItems.isEmpty()) {
                Text(
                    "No items detected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Raw text toggle
        if (rawText != null) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRawText = !showRawText }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Raw OCR Text",
                    style = MaterialTheme.typography.titleSmall
                )
                Icon(
                    if (showRawText) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            if (showRawText) {
                Text(
                    rawText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        // Action buttons
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isEditing) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
                Button(
                    onClick = onSaveChanges,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            } else {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan Another")
                }
                Button(
                    onClick = { /* TODO: Add to shopping/inventory */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Inventory, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add to Pantry")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Receipt") },
            text = { Text("Are you sure you want to delete this receipt?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatusChip(status: ReceiptStatus) {
    val (backgroundColor, contentColor) = when (status) {
        ReceiptStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ReceiptStatus.NEEDS_REVIEW -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        ReceiptStatus.PROCESSING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ReceiptStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        ReceiptStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            status.name.replace("_", " "),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

@Composable
private fun LineItemRow(
    item: ReceiptLineItem,
    isEditing: Boolean,
    onUpdate: (ReceiptLineItem) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (item.isDiscount) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            )
            if (item.quantity != 1.0 || item.unitPrice != null) {
                Text(
                    buildString {
                        if (item.quantity != 1.0) append("Qty: ${item.quantity}")
                        item.unitPrice?.let {
                            if (item.quantity != 1.0) append(" @ ")
                            append(item.formattedUnitPrice)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (item.isDiscount) "-${item.formattedPrice}" else item.formattedPrice,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (item.isDiscount) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            )

            if (isEditing) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentReceiptsList(
    receipts: List<ReceiptEntity>,
    onReceiptClick: (ReceiptEntity) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recent Receipts",
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        if (receipts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No receipts yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn {
                itemsIndexed(receipts) { _, receipt ->
                    ListItem(
                        headlineContent = {
                            Text(receipt.storeName ?: "Unknown Store")
                        },
                        supportingContent = {
                            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                            Text(dateFormat.format(Date(receipt.scannedAt)))
                        },
                        trailingContent = {
                            Text(
                                receipt.formattedTotal ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingContent = {
                            StatusChip(status = receipt.status)
                        },
                        modifier = Modifier.clickable { onReceiptClick(receipt) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

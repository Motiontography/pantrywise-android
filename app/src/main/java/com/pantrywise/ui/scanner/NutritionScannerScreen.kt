package com.pantrywise.ui.scanner

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pantrywise.data.local.entity.NutritionEntity
import com.pantrywise.services.LabelFormat
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NutritionScannerScreen(
    productId: String?,
    productName: String?,
    onNavigateBack: () -> Unit,
    onNutritionSaved: (NutritionEntity) -> Unit,
    viewModel: NutritionScannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Set product on first composition
    LaunchedEffect(productId) {
        if (productId != null) {
            viewModel.setProduct(productId, productName)
        }
    }

    // Handle events
    LaunchedEffect(uiState.event) {
        when (val event = uiState.event) {
            is NutritionScannerEvent.NutritionParsed -> {
                val confidence = (event.nutrition.confidence * 100).toInt()
                val fieldCount = event.nutrition.fieldCount
                Toast.makeText(
                    context,
                    "Found $fieldCount nutrition fields ($confidence% confidence)",
                    Toast.LENGTH_LONG
                ).show()
            }
            is NutritionScannerEvent.NutritionSaved -> {
                Toast.makeText(context, "Nutrition data saved!", Toast.LENGTH_SHORT).show()
                onNutritionSaved(event.entity)
            }
            is NutritionScannerEvent.Error -> {
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
        if (uiState.event != NutritionScannerEvent.None) {
            viewModel.clearEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutrition Scanner") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!uiState.isCapturing && uiState.parsedNutrition != null) {
                        IconButton(onClick = { viewModel.toggleEditing() }) {
                            Icon(
                                if (uiState.isEditing) Icons.Default.Done else Icons.Default.Edit,
                                contentDescription = if (uiState.isEditing) "Done" else "Edit"
                            )
                        }
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
                productId == null -> {
                    NoProductView(onNavigateBack = onNavigateBack)
                }
                uiState.isProcessing -> {
                    ProcessingView()
                }
                uiState.isCapturing -> {
                    if (cameraPermissionState.status.isGranted) {
                        CaptureView(
                            isFlashlightOn = uiState.isFlashlightOn,
                            onToggleFlashlight = { viewModel.toggleFlashlight() },
                            onImageCaptured = { uri, bytes ->
                                viewModel.onImageCaptured(uri, bytes, context)
                            }
                        )
                    } else {
                        PermissionRequestView(
                            onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                        )
                    }
                }
                uiState.parsedNutrition != null -> {
                    ResultView(
                        uiState = uiState,
                        onRetake = { viewModel.retakePhoto() },
                        onSave = { viewModel.saveNutrition() },
                        onServingSizeChange = { viewModel.updateServingSize(it) },
                        onServingUnitChange = { viewModel.updateServingUnit(it) },
                        onServingsPerContainerChange = { viewModel.updateServingsPerContainer(it) },
                        onCaloriesChange = { viewModel.updateCalories(it) },
                        onTotalFatChange = { viewModel.updateTotalFat(it) },
                        onSaturatedFatChange = { viewModel.updateSaturatedFat(it) },
                        onTransFatChange = { viewModel.updateTransFat(it) },
                        onCholesterolChange = { viewModel.updateCholesterol(it) },
                        onSodiumChange = { viewModel.updateSodium(it) },
                        onTotalCarbsChange = { viewModel.updateTotalCarbs(it) },
                        onFiberChange = { viewModel.updateFiber(it) },
                        onSugarsChange = { viewModel.updateSugars(it) },
                        onAddedSugarsChange = { viewModel.updateAddedSugars(it) },
                        onProteinChange = { viewModel.updateProtein(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoProductView(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "No Product Selected",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Please select a product first to scan its nutrition label",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigateBack) {
            Text("Go Back")
        }
    }
}

@Composable
private fun CaptureView(
    isFlashlightOn: Boolean,
    onToggleFlashlight: () -> Unit,
    onImageCaptured: (Uri, ByteArray) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Column(modifier = Modifier.fillMaxSize()) {
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
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                            camera.cameraControl.enableTorch(isFlashlightOn)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )

            // Nutrition label overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    // Corner markers
                    CornerMarker(
                        modifier = Modifier.align(Alignment.TopStart),
                        rotation = 0f
                    )
                    CornerMarker(
                        modifier = Modifier.align(Alignment.TopEnd),
                        rotation = 90f
                    )
                    CornerMarker(
                        modifier = Modifier.align(Alignment.BottomStart),
                        rotation = 270f
                    )
                    CornerMarker(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        rotation = 180f
                    )

                    // Center label
                    Text(
                        "Nutrition Facts",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Instructions
            Text(
                text = "Align nutrition label within frame",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
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
                // Flashlight toggle
                IconButton(
                    onClick = onToggleFlashlight,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isFlashlightOn) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        if (isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle flashlight",
                        tint = if (isFlashlightOn) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Capture button
                Button(
                    onClick = {
                        val photoFile = File(
                            context.cacheDir,
                            "nutrition_${System.currentTimeMillis()}.jpg"
                        )

                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        imageCapture?.takePicture(
                            outputOptions,
                            cameraExecutor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    val savedUri = Uri.fromFile(photoFile)
                                    val bytes = photoFile.readBytes()
                                    onImageCaptured(savedUri, bytes)
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
private fun CornerMarker(modifier: Modifier = Modifier, rotation: Float) {
    Box(
        modifier = modifier
            .size(24.dp)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary)
        )
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
            "Scanning nutrition label...",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Reading text from image",
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
            "To scan nutrition labels, please allow camera access",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultView(
    uiState: NutritionScannerUiState,
    onRetake: () -> Unit,
    onSave: () -> Unit,
    onServingSizeChange: (String) -> Unit,
    onServingUnitChange: (String) -> Unit,
    onServingsPerContainerChange: (String) -> Unit,
    onCaloriesChange: (String) -> Unit,
    onTotalFatChange: (String) -> Unit,
    onSaturatedFatChange: (String) -> Unit,
    onTransFatChange: (String) -> Unit,
    onCholesterolChange: (String) -> Unit,
    onSodiumChange: (String) -> Unit,
    onTotalCarbsChange: (String) -> Unit,
    onFiberChange: (String) -> Unit,
    onSugarsChange: (String) -> Unit,
    onAddedSugarsChange: (String) -> Unit,
    onProteinChange: (String) -> Unit
) {
    val parsedNutrition = uiState.parsedNutrition ?: return
    var unitExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Image preview
        uiState.capturedImageUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = "Nutrition label",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                contentScale = ContentScale.Crop
            )
        }

        // Confidence and format info
        Surface(
            color = if (parsedNutrition.confidence >= 0.6f)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (parsedNutrition.confidence >= 0.6f) Icons.Default.CheckCircle
                    else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Confidence: ${(parsedNutrition.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Format: ${parsedNutrition.labelFormat.displayName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "${parsedNutrition.fieldCount} fields",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Product name
        uiState.productName?.let { name ->
            Text(
                "Product: $name",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        HorizontalDivider()

        // Nutrition Facts Card (FDA-style layout)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Nutrition Facts",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(thickness = 8.dp, modifier = Modifier.padding(vertical = 4.dp))

                // Serving size
                if (uiState.isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.editedServingSize,
                            onValueChange = onServingSizeChange,
                            label = { Text("Serving Size") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        ExposedDropdownMenuBox(
                            expanded = unitExpanded,
                            onExpandedChange = { unitExpanded = !unitExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = uiState.editedServingUnit,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Unit") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = unitExpanded,
                                onDismissRequest = { unitExpanded = false }
                            ) {
                                NutritionScannerViewModel.SERVING_UNITS.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit) },
                                        onClick = {
                                            onServingUnitChange(unit)
                                            unitExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.editedServingsPerContainer,
                        onValueChange = onServingsPerContainerChange,
                        label = { Text("Servings Per Container") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                } else {
                    Text(
                        buildString {
                            append("Serving size ")
                            append(uiState.editedServingSize.ifBlank { "—" })
                            if (uiState.editedServingUnit.isNotBlank()) {
                                append(" ${uiState.editedServingUnit}")
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (uiState.editedServingsPerContainer.isNotBlank()) {
                        Text(
                            "${uiState.editedServingsPerContainer} servings per container",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                HorizontalDivider(thickness = 4.dp, modifier = Modifier.padding(vertical = 8.dp))

                // Calories
                NutritionRow(
                    label = "Calories",
                    value = uiState.editedCalories,
                    unit = "",
                    isEditing = uiState.isEditing,
                    onValueChange = onCaloriesChange,
                    isBold = true
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Fat section
                NutritionRow(
                    label = "Total Fat",
                    value = uiState.editedTotalFat,
                    unit = "g",
                    isEditing = uiState.isEditing,
                    onValueChange = onTotalFatChange,
                    isBold = true
                )
                NutritionRow(
                    label = "Saturated Fat",
                    value = uiState.editedSaturatedFat,
                    unit = "g",
                    isEditing = uiState.isEditing,
                    onValueChange = onSaturatedFatChange,
                    isIndented = true
                )
                NutritionRow(
                    label = "Trans Fat",
                    value = uiState.editedTransFat,
                    unit = "g",
                    isEditing = uiState.isEditing,
                    onValueChange = onTransFatChange,
                    isIndented = true
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Cholesterol and Sodium
                NutritionRow(
                    label = "Cholesterol",
                    value = uiState.editedCholesterol,
                    unit = "mg",
                    isEditing = uiState.isEditing,
                    onValueChange = onCholesterolChange,
                    isBold = true
                )
                NutritionRow(
                    label = "Sodium",
                    value = uiState.editedSodium,
                    unit = "mg",
                    isEditing = uiState.isEditing,
                    onValueChange = onSodiumChange,
                    isBold = true
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Carbs section
                NutritionRow(
                    label = "Total Carbohydrate",
                    value = uiState.editedTotalCarbs,
                    unit = "g",
                    isEditing = uiState.isEditing,
                    onValueChange = onTotalCarbsChange,
                    isBold = true
                )
                NutritionRow(
                    label = "Dietary Fiber",
                    value = uiState.editedFiber,
                    unit = "g",
                    isEditing = uiState.isEditing,
                    onValueChange = onFiberChange,
                    isIndented = true
                )
                NutritionRow(
                    label = "Total Sugars",
                    value = uiState.editedSugars,
                    unit = "g",
                    isEditing = uiState.isEditing,
                    onValueChange = onSugarsChange,
                    isIndented = true
                )
                NutritionRow(
                    label = "Added Sugars",
                    value = uiState.editedAddedSugars,
                    unit = "g",
                    isEditing = uiState.isEditing,
                    onValueChange = onAddedSugarsChange,
                    isIndented = true,
                    indentLevel = 2
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Protein
                NutritionRow(
                    label = "Protein",
                    value = uiState.editedProtein,
                    unit = "g",
                    isEditing = uiState.isEditing,
                    onValueChange = onProteinChange,
                    isBold = true
                )

                HorizontalDivider(thickness = 8.dp, modifier = Modifier.padding(vertical = 8.dp))

                // Vitamins and minerals (compact display)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MicronutrientChip("Vit D", uiState.editedVitaminD, "mcg")
                    MicronutrientChip("Calcium", uiState.editedCalcium, "mg")
                    MicronutrientChip("Iron", uiState.editedIron, "mg")
                    MicronutrientChip("Potassium", uiState.editedPotassium, "mg")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retake")
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                enabled = parsedNutrition.hasMinimumData ||
                        uiState.editedCalories.isNotBlank() ||
                        uiState.editedProtein.isNotBlank()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save")
            }
        }
    }
}

@Composable
private fun NutritionRow(
    label: String,
    value: String,
    unit: String,
    isEditing: Boolean,
    onValueChange: (String) -> Unit,
    isBold: Boolean = false,
    isIndented: Boolean = false,
    indentLevel: Int = 1
) {
    val indentPadding = if (isIndented) (indentLevel * 16).dp else 0.dp

    if (isEditing) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = indentPadding, top = 4.dp, bottom = 4.dp),
            suffix = { if (unit.isNotBlank()) Text(unit) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = indentPadding, top = 2.dp, end = 0.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                if (value.isNotBlank()) "$value$unit" else "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun MicronutrientChip(label: String, value: String, unit: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                if (value.isNotBlank()) "$value$unit" else "—",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

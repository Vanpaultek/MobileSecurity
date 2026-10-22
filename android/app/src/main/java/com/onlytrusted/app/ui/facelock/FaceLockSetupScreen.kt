// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.facelock

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.onlytrusted.app.ui.subscription.subGold
import com.onlytrusted.app.ui.theme.BrandAccentDark
import com.onlytrusted.app.ui.theme.BrandCyanDark
import com.onlytrusted.app.ui.theme.DangerDark
import com.onlytrusted.app.ui.theme.MidnightBackground
import com.onlytrusted.app.ui.theme.MidnightCard
import com.onlytrusted.app.ui.theme.MidnightElevated
import com.onlytrusted.app.ui.theme.PrimaryTextDark
import com.onlytrusted.app.ui.theme.SecondaryTextDark
import java.util.concurrent.Executors

/**
 * Face-Change Shoulder-Surfing Lock (Elite) setup screen. Caller gates on
 * `SubscriptionState.hasElite`; this composable assumes the user is on Elite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceLockSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var enrolled by remember { mutableStateOf(FaceLockState.isEnrolled(context)) }
    var lockOwnerLeaves by remember {
        mutableStateOf(FaceLockState.lockWhenOwnerLeaves(context))
    }
    var lockStrangerJoins by remember {
        mutableStateOf(FaceLockState.lockWhenStrangerJoins(context))
    }
    var blurOnExit by remember { mutableStateOf(FaceLockState.blurOnExit(context)) }
    var showEnrollDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MidnightBackground,
        topBar = {
            TopAppBar(
                title = { Text("Face-Change Lock", color = PrimaryTextDark) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = BrandCyanDark) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderCard()

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Owner enrollment",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryTextDark
                    )
                    if (enrolled) {
                        Text(
                            "Owner face enrolled.",
                            fontSize = 13.sp,
                            color = SecondaryTextDark
                        )
                        OutlinedButton(
                            onClick = { showEnrollDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Re-enroll", color = BrandAccentDark)
                        }
                    } else {
                        Text(
                            "Enroll your face so OnlyTrusted can tell when the person in front of the camera isn't you.",
                            fontSize = 13.sp,
                            color = SecondaryTextDark
                        )
                        Button(
                            onClick = { showEnrollDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enroll your face", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "What triggers the lock",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryTextDark
                    )
                    ToggleRow(
                        title = "Lock when owner leaves frame",
                        detail = "Hide content after 3 seconds with no face in view.",
                        checked = lockOwnerLeaves,
                        onChange = {
                            lockOwnerLeaves = it
                            FaceLockState.setLockWhenOwnerLeaves(context, it)
                        }
                    )
                    ToggleRow(
                        title = "Lock when stranger joins frame",
                        detail = "Instant lock + alert if a non-owner or extra face is seen.",
                        checked = lockStrangerJoins,
                        onChange = {
                            lockStrangerJoins = it
                            FaceLockState.setLockWhenStrangerJoins(context, it)
                        }
                    )
                    ToggleRow(
                        title = "Blur sensitive screens on exit",
                        detail = "Overlay a soft blur instead of a hard lock when you walk away.",
                        checked = blurOnExit,
                        onChange = {
                            blurOnExit = it
                            FaceLockState.setBlurOnExit(context, it)
                        }
                    )
                }
            }

            if (enrolled) {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerDark)
                ) {
                    Text("Remove enrollment", color = DangerDark)
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        if (showEnrollDialog) {
            FaceLockEnrollmentDialog(
                onCancel = { showEnrollDialog = false },
                onDone = {
                    showEnrollDialog = false
                    enrolled = FaceLockState.isEnrolled(context)
                }
            )
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                containerColor = MidnightCard,
                title = { Text("Remove enrollment?", color = PrimaryTextDark) },
                text = {
                    Text(
                        "You'll need to re-enroll your face before OnlyTrusted can tell you apart from strangers again.",
                        color = SecondaryTextDark
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showClearDialog = false
                        FaceLockState.clearEnrollment(context)
                        enrolled = false
                    }) { Text("Remove", color = DangerDark, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun HeaderCard() {
    Card {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BrandAccentDark,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.FaceRetouchingNatural,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column {
                Text("Elite feature", fontSize = 11.sp, color = subGold, fontWeight = FontWeight.SemiBold)
                Text(
                    "Guards against over-the-shoulder peeks",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextDark
                )
                Text(
                    "While sensitive screens are open, the front camera watches for faces. It blurs when you walk away and locks the moment someone else looks at your screen. Images are processed in memory and never stored.",
                    fontSize = 12.sp,
                    color = SecondaryTextDark
                )
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightElevated,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = PrimaryTextDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = SecondaryTextDark, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandAccentDark
            )
        )
    }
}

// ---------- Enrollment dialog -------------------------------------------------

/** Three-pose capture dialog. Averages the three samples into the owner embedding. */
@Composable
private fun FaceLockEnrollmentDialog(
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCamera = granted
        if (!granted) onCancel()
    }
    LaunchedEffect(Unit) {
        if (!hasCamera) permLauncher.launch(Manifest.permission.CAMERA)
    }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MidnightCard,
        title = { Text("Enroll your face", color = PrimaryTextDark, fontWeight = FontWeight.Bold) },
        text = {
            if (!hasCamera) {
                Text(
                    "Camera permission is required to enroll your face.",
                    color = SecondaryTextDark
                )
            } else {
                EnrollmentCapturePane(
                    context = context,
                    onDone = onDone
                )
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun EnrollmentCapturePane(
    context: Context,
    onDone: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build()
        )
    }
    val samples = remember { mutableListOf<FloatArray>() }
    var stage by remember { mutableIntStateOf(0) }
    var stableHits by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    val prompts = listOf(
        "Look straight at the camera",
        "Turn your head slightly left",
        "Turn your head slightly right"
    )

    DisposableEffect(Unit) {
        onDispose {
            runCatching { detector.close() }
            executor.shutdown()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (finished) "Three angles captured. Tap Save to store your enrollment."
            else prompts.getOrNull(stage) ?: "",
            color = PrimaryTextDark,
            fontWeight = FontWeight.SemiBold
        )
        LinearProgressIndicator(
            progress = { stage.coerceAtMost(3) / 3f },
            color = BrandAccentDark,
            modifier = Modifier.fillMaxWidth()
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(Color.Black, RoundedCornerShape(12.dp)),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build().also { ia ->
                            ia.setAnalyzer(executor) { proxy ->
                                if (finished) {
                                    proxy.close()
                                    return@setAnalyzer
                                }
                                val media = proxy.image
                                if (media == null) {
                                    proxy.close()
                                    return@setAnalyzer
                                }
                                val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                detector.process(img)
                                    .addOnSuccessListener { faces ->
                                        handleFaces(faces, samples,
                                            { stage },
                                            { stableHits },
                                            { stableHits = it },
                                            { stage = it; stableHits = 0 },
                                            { finished = true }
                                        )
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            }
                        }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            analysis
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )
        if (finished) {
            Button(
                onClick = {
                    FaceLockState.setEnrollment(context, samples.toList())
                    onDone()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandAccentDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save enrollment", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun handleFaces(
    faces: List<Face>,
    samples: MutableList<FloatArray>,
    stage: () -> Int,
    stableHits: () -> Int,
    setStableHits: (Int) -> Unit,
    setStage: (Int) -> Unit,
    onFinished: () -> Unit
) {
    if (faces.size != 1) {
        setStableHits(0)
        return
    }
    val embedding = FaceLockEmbedder.embed(faces[0])
    if (embedding == null) {
        setStableHits(0)
        return
    }
    val hits = stableHits() + 1
    if (hits < STABLE_FRAMES_REQUIRED) {
        setStableHits(hits)
        return
    }
    samples.add(embedding)
    val next = stage() + 1
    if (next >= TOTAL_STAGES) {
        onFinished()
    } else {
        setStage(next)
    }
}

private const val STABLE_FRAMES_REQUIRED = 3
private const val TOTAL_STAGES = 3

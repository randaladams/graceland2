package com.randaladams.graceland

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val GoldAccent = Color(0xFFE8C266)
private val DeepObsidian = Color(0xFF10131C)
private val GlassSurface = Color(0xB30D111A)
private val GlassBorder = Color(0x44FFFFFF)

@Composable
fun GracelandScreen() {
    val context = LocalContext.current
    val preferences = remember { UserPreferences(context) }
    val backgroundRepo = remember { BackgroundRepository(context) }
    val locationTracker = remember { LocationTracker(context) }
    val compassManager = remember { CompassSensorManager(context) }

    var measurementSystem by remember { mutableStateOf(preferences.getMeasurementSystem()) }
    var nowInstant by remember { mutableStateOf(ZonedDateTime.now()) }
    var isPeekingBackground by remember { mutableStateOf(false) }

    val uiAlpha by animateFloatAsState(
        targetValue = if (isPeekingBackground) 0f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "peekAlpha"
    )

    // Live 1-second clock tick for both Memphis and User Local Time
    LaunchedEffect(Unit) {
        while (true) {
            nowInstant = ZonedDateTime.now()
            delay(1000L)
        }
    }

    val memphisTime = remember(nowInstant) {
        nowInstant.withZoneSameInstant(GracelandMath.MEMPHIS_ZONE_ID)
    }
    val userZoneId = remember(nowInstant) { ZoneId.systemDefault() }
    val userLocalTime = remember(nowInstant, userZoneId) {
        nowInstant.withZoneSameInstant(userZoneId)
    }

    val deviceLocale = Locale.getDefault()
    val use24Hour = DateFormat.is24HourFormat(context)
    val datePattern = remember(deviceLocale) {
        DateFormat.getBestDateTimePattern(deviceLocale, "EMMMd")
    }

    // Background strictly follows the time of day in Memphis
    val activePeriod = GracelandMath.periodForHour(memphisTime.hour)
    val assetPaths = remember(activePeriod) {
        backgroundRepo.listAssetPaths(activePeriod)
    }
    var photoIndex by remember(activePeriod) { mutableIntStateOf(0) }

    LaunchedEffect(activePeriod, assetPaths.size) {
        if (assetPaths.size > 1) {
            while (true) {
                delay(12_000L)
                photoIndex = (photoIndex + 1) % assetPaths.size
            }
        }
    }

    val currentAssetPath = assetPaths.getOrNull(photoIndex % assetPaths.size.coerceAtLeast(1))

    // Location & Tracking State
    var isTracking by rememberSaveable { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(hasAnyLocationPermission(context))
    }
    var permissionDeniedOnce by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var userPlaceLabel by remember { mutableStateOf<String?>(null) }
    var compassReading by remember {
        mutableStateOf(CompassReading(azimuthDegrees = 0.0, lowAccuracy = false, hasSensor = true))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (!granted) {
            permissionDeniedOnce = true
        }
    }

    LaunchedEffect(isTracking, hasLocationPermission) {
        if (isTracking && hasLocationPermission) {
            locationTracker.observeLocation().collect { loc ->
                currentLocation = loc
            }
        }
    }

    LaunchedEffect(currentLocation?.latitude, currentLocation?.longitude) {
        val loc = currentLocation ?: return@LaunchedEffect
        val resolved = locationTracker.resolvePlaceLabel(loc.latitude, loc.longitude)
        if (!resolved.isNullOrBlank()) {
            userPlaceLabel = resolved
        }
    }

    LaunchedEffect(isTracking, currentLocation?.latitude, currentLocation?.longitude) {
        if (isTracking) {
            compassManager.observeAzimuth(
                userLat = currentLocation?.latitude,
                userLon = currentLocation?.longitude,
                userAltMeters = currentLocation?.altitude ?: 0.0
            ).collect { reading ->
                compassReading = reading
            }
        }
    }

    // Distance & Bearing to Graceland (WGS84)
    val distanceAndBearing = remember(currentLocation) {
        currentLocation?.let { loc ->
            val results = FloatArray(2)
            Location.distanceBetween(
                loc.latitude,
                loc.longitude,
                GracelandMath.GRACELAND_LAT,
                GracelandMath.GRACELAND_LON,
                results
            )
            results[0].toDouble() to GracelandMath.normalizeDegrees(results[1].toDouble())
        }
    }

    // Continuous unwrapped angle for silky compass animation across 0/360 boundary
    var unwrappedArrowDeg by remember { mutableDoubleStateOf(0.0) }
    val targetRelativeAngle = distanceAndBearing?.let { (_, bearing) ->
        if (compassReading.hasSensor) {
            GracelandMath.relativeArrowAngle(bearing, compassReading.azimuthDegrees)
        } else {
            bearing
        }
    } ?: 0.0

    LaunchedEffect(targetRelativeAngle) {
        val delta = GracelandMath.shortestAngleDelta(
            GracelandMath.normalizeDegrees(unwrappedArrowDeg),
            targetRelativeAngle
        )
        unwrappedArrowDeg += delta
    }

    val animatedArrowRotation by animateFloatAsState(
        targetValue = unwrappedArrowDeg.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "arrowRotation"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepObsidian)
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }

        var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(currentAssetPath, widthPx, heightPx) {
            if (currentAssetPath != null) {
                activeBitmap = backgroundRepo.loadScaledBitmap(currentAssetPath, widthPx, heightPx)
            }
        }

        // Full-screen crossfading Graceland photo (always 100% visible)
        Crossfade(
            targetState = activeBitmap,
            animationSpec = tween(durationMillis = 1200),
            label = "backgroundPhoto",
            modifier = Modifier.fillMaxSize()
        ) { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Graceland ${activePeriod.displayName} photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1A2238), Color(0xFF0E121D))
                            )
                        )
                )
            }
        }

        // Scrim overlay (fades out completely when holding the Eye button)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(uiAlpha)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xB3090C12),
                            0.35f to Color(0x4D090C12),
                            0.70f to Color(0x66090C12),
                            1.0f to Color(0xBF090C12)
                        )
                    )
                )
        )

        // Foreground HUD & Interactive Center
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Two-Line Time Box (Tap to toggle Imperial / Metric distance)
            Box(modifier = Modifier.alpha(uiAlpha)) {
                TwoLineTimeBox(
                    elvisTimeText = GracelandMath.formatDateTimeWithPattern(
                        dateTime = memphisTime,
                        datePattern = datePattern,
                        use24Hour = use24Hour,
                        locale = deviceLocale
                    ),
                    localTimeText = GracelandMath.formatDateTimeWithPattern(
                        dateTime = userLocalTime,
                        datePattern = datePattern,
                        use24Hour = use24Hour,
                        locale = deviceLocale
                    ),
                    userPlaceLabel = userPlaceLabel,
                    measurementSystem = measurementSystem,
                    onToggleUnits = {
                        val next = if (measurementSystem == GracelandMath.MeasurementSystem.IMPERIAL) {
                            GracelandMath.MeasurementSystem.METRIC
                        } else {
                            GracelandMath.MeasurementSystem.IMPERIAL
                        }
                        measurementSystem = next
                        preferences.setMeasurementSystem(next)
                    }
                )
            }

            // Eye-shaped button right below the time box on the right side
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                EyePeekButton(
                    alpha = uiAlpha,
                    onPeekStateChange = { peeking -> isPeekingBackground = peeking }
                )
            }

            // Center Area: Hero Button OR Live Compass + Distance
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .alpha(uiAlpha),
                contentAlignment = Alignment.Center
            ) {
                if (!isTracking) {
                    AskGracelandHeroButton(
                        onClick = {
                            if (isPeekingBackground) return@AskGracelandHeroButton
                            isTracking = true
                            hasLocationPermission = hasAnyLocationPermission(context)
                            if (!hasLocationPermission) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    )
                } else if (!hasLocationPermission) {
                    PermissionPromptCard(
                        permissionDeniedOnce = permissionDeniedOnce,
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onOpenSettings = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        },
                        onBack = { isTracking = false }
                    )
                } else if (distanceAndBearing == null) {
                    LocatingCard(onCancel = { isTracking = false })
                } else {
                    val (distanceMeters, bearingDeg) = distanceAndBearing
                    CompassDistanceCard(
                        distanceText = GracelandMath.formatDistance(
                            distanceMeters = distanceMeters,
                            system = measurementSystem,
                            locale = deviceLocale
                        ),
                        bearingDegrees = bearingDeg,
                        deviceAzimuthDegrees = compassReading.azimuthDegrees,
                        arrowRotationDegrees = animatedArrowRotation,
                        hasCompassSensor = compassReading.hasSensor,
                        lowCompassAccuracy = compassReading.lowAccuracy,
                        onReset = { isTracking = false }
                    )
                }
            }

            // Reserved bottom slot for future AdMob Banner Ad (50dp banner + margin)
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            )
        }
    }
}

@Composable
private fun TwoLineTimeBox(
    elvisTimeText: String,
    localTimeText: String,
    userPlaceLabel: String?,
    measurementSystem: GracelandMath.MeasurementSystem,
    onToggleUnits: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onToggleUnits)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = GoldAccent, fontWeight = FontWeight.Bold)) {
                            append("Elvis Time: ")
                        }
                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) {
                            append(elvisTimeText)
                        }
                    },
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val placeSuffix = userPlaceLabel?.let { " ($it)" } ?: ""
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = GoldAccent.copy(alpha = 0.9f), fontWeight = FontWeight.Bold)) {
                            append("Local Time: ")
                        }
                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.95f), fontWeight = FontWeight.Medium)) {
                            append("$localTimeText$placeSuffix")
                        }
                    },
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GoldAccent.copy(alpha = 0.22f))
                    .border(1.dp, GoldAccent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (measurementSystem == GracelandMath.MeasurementSystem.IMPERIAL) "MI" else "KM",
                    color = GoldAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun EyePeekButton(
    alpha: Float,
    onPeekStateChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .alpha(alpha)
            .size(width = 52.dp, height = 36.dp)
            .clip(CircleShape)
            .background(GlassSurface)
            .border(1.dp, GoldAccent.copy(alpha = 0.75f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPeekStateChange(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            onPeekStateChange(false)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 30.dp, height = 18.dp)) {
            val w = size.width
            val h = size.height
            val cy = h / 2f
            val eyePath = Path().apply {
                moveTo(0f, cy)
                cubicTo(w * 0.24f, 0f, w * 0.76f, 0f, w, cy)
                cubicTo(w * 0.76f, h, w * 0.24f, h, 0f, cy)
                close()
            }
            drawPath(
                path = eyePath,
                color = GoldAccent,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawCircle(
                color = GoldAccent,
                radius = h * 0.26f,
                center = Offset(w / 2f, cy)
            )
            drawCircle(
                color = DeepObsidian,
                radius = h * 0.09f,
                center = Offset(w / 2f + h * 0.08f, cy - h * 0.08f)
            )
        }
    }
}

@Composable
private fun AskGracelandHeroButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = GoldAccent,
            contentColor = DeepObsidian
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 12.dp,
            pressedElevation = 4.dp
        ),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
        modifier = Modifier
            .border(2.dp, Color.White.copy(alpha = 0.45f), CircleShape)
    ) {
        Icon(
            imageVector = Icons.Rounded.Explore,
            contentDescription = null,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "How far is Graceland?",
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun CompassDistanceCard(
    distanceText: String,
    bearingDegrees: Double,
    deviceAzimuthDegrees: Double,
    arrowRotationDegrees: Float,
    hasCompassSensor: Boolean,
    lowCompassAccuracy: Boolean,
    onReset: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "GRACELAND • MEMPHIS, TN",
                color = GoldAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp
            )

            CompassArrowDial(
                arrowRotationDegrees = arrowRotationDegrees,
                modifier = Modifier.size(188.dp)
            )

            Text(
                text = distanceText,
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            val bearingCardinal = GracelandMath.compassCardinal(bearingDegrees)
            val headingCardinal = GracelandMath.compassCardinal(deviceAzimuthDegrees)
            val subtitle = if (hasCompassSensor) {
                "Bearing ${bearingDegrees.roundToInt()}° $bearingCardinal  •  Phone facing ${deviceAzimuthDegrees.roundToInt()}° $headingCardinal"
            } else {
                "Bearing ${bearingDegrees.roundToInt()}° $bearingCardinal  •  No compass sensor on device"
            }
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            if (lowCompassAccuracy && hasCompassSensor) {
                Text(
                    text = "Tip: Move phone in a figure-8 to calibrate compass",
                    color = GoldAccent.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            OutlinedButton(
                onClick = onReset,
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Hide Compass", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CompassArrowDial(
    arrowRotationDegrees: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
                color = Color(0x66090C12),
                radius = radius * 0.94f,
                center = center
            )
            drawCircle(
                color = GoldAccent.copy(alpha = 0.45f),
                radius = radius * 0.94f,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            for (i in 0 until 12) {
                val angleRad = Math.toRadians((i * 30).toDouble())
                val innerR = if (i % 3 == 0) radius * 0.80f else radius * 0.86f
                val outerR = radius * 0.92f
                val start = Offset(
                    center.x + (innerR * sin(angleRad)).toFloat(),
                    center.y - (innerR * cos(angleRad)).toFloat()
                )
                val end = Offset(
                    center.x + (outerR * sin(angleRad)).toFloat(),
                    center.y - (outerR * cos(angleRad)).toFloat()
                )
                drawLine(
                    color = if (i % 3 == 0) GoldAccent else Color.White.copy(alpha = 0.4f),
                    start = start,
                    end = end,
                    strokeWidth = if (i % 3 == 0) 2.5.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            rotate(degrees = arrowRotationDegrees, pivot = center) {
                val tipY = center.y - radius * 0.68f
                val tailY = center.y + radius * 0.48f
                val wingX = radius * 0.23f
                val notchY = center.y + radius * 0.24f

                val arrowPath = Path().apply {
                    moveTo(center.x, tipY)
                    lineTo(center.x + wingX, tailY)
                    lineTo(center.x, notchY)
                    lineTo(center.x - wingX, tailY)
                    close()
                }
                drawPath(path = arrowPath, color = GoldAccent)
                drawPath(
                    path = arrowPath,
                    color = Color.White.copy(alpha = 0.65f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            drawCircle(
                color = DeepObsidian,
                radius = 7.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = center
            )
        }
    }
}

@Composable
private fun LocatingCard(onCancel: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = GoldAccent)
            Text(
                text = "Getting your GPS position...",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedButton(
                onClick = onCancel,
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun PermissionPromptCard(
    permissionDeniedOnce: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Location Permission Needed",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "We use your device location to calculate the distance and compass direction to Graceland in Memphis.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Back")
                }
                Button(
                    onClick = if (permissionDeniedOnce) onOpenSettings else onRequestPermission,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldAccent,
                        contentColor = DeepObsidian
                    )
                ) {
                    Text(
                        text = if (permissionDeniedOnce) "Open Settings" else "Allow Location",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun hasAnyLocationPermission(context: android.content.Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

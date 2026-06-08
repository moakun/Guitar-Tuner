package com.tuner.gtuner.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuner.gtuner.audio.AudioEngine
import com.tuner.gtuner.audio.TonePlayer
import com.tuner.gtuner.data.Tuning
import com.tuner.gtuner.data.Tunings
import com.tuner.gtuner.pitch.PitchMath
import com.tuner.gtuner.pitch.TuningGuide
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val InTuneGreen = Color(0xFF34D399)
private val OffColor = Color(0xFFEF5350)
private const val IN_TUNE_CENTS = 5.0
private const val A4_MIN = 415
private const val A4_MAX = 466

/**
 * Tuner screen. Pick "Chromatic" to read any note, or a named tuning to be guided
 * string-by-string. Adapts between a stacked portrait layout and a side-by-side
 * landscape/tablet layout.
 */
@Composable
fun TunerScreen(modifier: Modifier = Modifier) {
    val engine = remember { AudioEngine() }
    val tonePlayer = remember { TonePlayer() }
    val pitch by engine.pitch.collectAsState()

    DisposableEffect(Unit) {
        engine.start()
        onDispose {
            engine.stop()
            tonePlayer.stop()
        }
    }

    val modeNames = remember { listOf("Chromatic") + Tunings.all.map { it.name } }
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    val tuning: Tuning? = if (selectedIndex == 0) null else Tunings.all[selectedIndex - 1]

    var a4 by rememberSaveable { mutableStateOf(440) }
    var lockedString by rememberSaveable(selectedIndex) { mutableStateOf(-1) }
    var tunedStrings by remember(selectedIndex, a4) { mutableStateOf(emptySet<Int>()) }
    var tonePlaying by remember { mutableStateOf(false) }

    val display = TuningGuide.display(pitch, tuning, a4.toDouble(), lockedString)
    val inTune = display.active && abs(display.cents) < IN_TUNE_CENTS

    // Anchor octave correction to a locked string so troublesome low strings lock to the
    // fundamental (e.g. low E to 82 Hz) instead of being read an octave high.
    LaunchedEffect(tuning, lockedString, a4) {
        val ref = if (tuning != null && lockedString in tuning.notes.indices) {
            tuning.frequencies(a4.toDouble())[lockedString].toFloat()
        } else {
            -1f
        }
        engine.setReference(ref)
    }

    // Mute the mic while the reference tone plays so we don't detect our own tone.
    LaunchedEffect(tonePlaying) {
        engine.setMuted(tonePlaying)
        if (tonePlaying) {
            delay(1600)
            tonePlaying = false
        }
    }

    // Auto-advance: hold in tune briefly, mark the string done, jump to the next when locked.
    LaunchedEffect(tuning, display.stringIndex, inTune, tonePlaying) {
        if (tuning != null && inTune && display.stringIndex >= 0 && !tonePlaying) {
            delay(700)
            tunedStrings = tunedStrings + display.stringIndex
            if (lockedString >= 0) {
                lockedString = tuning.notes.indices.firstOrNull { it !in tunedStrings } ?: lockedString
            }
        }
    }

    val targetFreq: Double? = when {
        tuning != null && display.stringIndex in tuning.notes.indices ->
            tuning.frequencies(a4.toDouble())[display.stringIndex]
        tuning == null && display.active ->
            PitchMath.midiToFrequency(PitchMath.analyze(pitch.toDouble(), a4.toDouble()).midi, a4.toDouble())
        else -> null
    }
    val playTone: () -> Unit = {
        targetFreq?.let {
            tonePlayer.play(it)
            tonePlaying = true
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        val wide = maxWidth > maxHeight

        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                GaugePane(
                    display = display,
                    inTune = inTune,
                    pitch = pitch,
                    canPlayTone = targetFreq != null,
                    onPlayTone = playTone,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                Spacer(Modifier.width(24.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Controls(
                        modeNames = modeNames,
                        selectedIndex = selectedIndex,
                        onSelectMode = { selectedIndex = it },
                        a4 = a4,
                        onA4 = { a4 = it.coerceIn(A4_MIN, A4_MAX) },
                        tuning = tuning,
                        display = display,
                        lockedString = lockedString,
                        tunedStrings = tunedStrings,
                        inTune = inTune,
                        onSelectString = { tapped ->
                            lockedString = if (lockedString == tapped) -1 else tapped
                        },
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Controls(
                    modeNames = modeNames,
                    selectedIndex = selectedIndex,
                    onSelectMode = { selectedIndex = it },
                    a4 = a4,
                    onA4 = { a4 = it.coerceIn(A4_MIN, A4_MAX) },
                    tuning = null, // strings rendered at the bottom in portrait
                    display = display,
                    lockedString = lockedString,
                    tunedStrings = tunedStrings,
                    inTune = inTune,
                    onSelectString = {},
                )
                GaugePane(
                    display = display,
                    inTune = inTune,
                    pitch = pitch,
                    canPlayTone = targetFreq != null,
                    onPlayTone = playTone,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                if (tuning != null) {
                    StringSelector(
                        tuning = tuning,
                        activeIndex = display.stringIndex,
                        lockedIndex = lockedString,
                        tuned = tunedStrings,
                        activeInTune = inTune,
                        onSelect = { tapped ->
                            lockedString = if (lockedString == tapped) -1 else tapped
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** The dial, the live readout, status line, and the reference-tone button. */
@Composable
private fun GaugePane(
    display: com.tuner.gtuner.pitch.TunerDisplay,
    inTune: Boolean,
    pitch: Float,
    canPlayTone: Boolean,
    onPlayTone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ArcGauge(
            label = display.label,
            cents = display.cents,
            active = display.active,
            inTune = inTune,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .aspectRatio(1.7f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                !display.active -> "Listening…"
                inTune -> "In tune"
                display.cents < 0 -> "♭ Tune up"
                else -> "♯ Tune down"
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (inTune) InTuneGreen else MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (display.active) String.format(Locale.US, "%.1f Hz", pitch.toDouble()) else "Play a string",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canPlayTone) {
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(onClick = onPlayTone) {
                    Text("♪ Tone")
                }
            }
        }
    }
}

/** Semicircular tuning dial: track, in-tune zone, ticks, a needle, and the note in the center. */
@Composable
private fun ArcGauge(
    label: String,
    cents: Double,
    active: Boolean,
    inTune: Boolean,
    modifier: Modifier = Modifier,
) {
    val target = if (active) cents.coerceIn(-50.0, 50.0).toFloat() else 0f
    val needle by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow),
        label = "needle",
    )
    val accent = when {
        !active -> MaterialTheme.colorScheme.outline
        inTune -> InTuneGreen
        else -> OffColor
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val zoneColor = InTuneGreen.copy(alpha = 0.22f)
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val onBg = MaterialTheme.colorScheme.onBackground
    val zoneDeg = (IN_TUNE_CENTS / 50.0 * 90.0).toFloat()

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.96f
            val r = minOf(size.width / 2f, cy) * 0.92f
            val stroke = r * 0.11f
            val topLeft = Offset(cx - r, cy - r)
            val arcSize = Size(2 * r, 2 * r)

            // Track (top semicircle) and the central in-tune zone.
            drawArc(trackColor, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                zoneColor, 270f - zoneDeg, 2 * zoneDeg, false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )

            // Ticks every 10 cents.
            for (c in -50..50 step 10) {
                val rad = Math.toRadians(90.0 - c / 50.0 * 90.0)
                val inner = r - stroke
                val outer = r + stroke * 0.15f
                drawLine(
                    if (c == 0) InTuneGreen else tickColor,
                    Offset(cx + (inner * cos(rad)).toFloat(), cy - (inner * sin(rad)).toFloat()),
                    Offset(cx + (outer * cos(rad)).toFloat(), cy - (outer * sin(rad)).toFloat()),
                    strokeWidth = if (c == 0) 5f else 3f,
                )
            }

            // Needle + hub.
            val rad = Math.toRadians(90.0 - needle / 50.0 * 90.0)
            val tip = Offset(cx + ((r - stroke * 0.4f) * cos(rad)).toFloat(), cy - ((r - stroke * 0.4f) * sin(rad)).toFloat())
            drawLine(accent, Offset(cx, cy), tip, strokeWidth = stroke * 0.45f, cap = StrokeCap.Round)
            drawCircle(accent, radius = stroke * 0.6f, center = Offset(cx, cy))
        }

        Column(
            modifier = Modifier.padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = if (inTune) InTuneGreen else onBg,
            )
            Text(
                text = if (active) String.format(Locale.US, "%+.1f¢", cents) else "",
                fontSize = 18.sp,
                color = accent,
            )
        }
    }
}

/** Tuning dropdown, calibration, and (when not portrait-bottom) the string selector. */
@Composable
private fun Controls(
    modeNames: List<String>,
    selectedIndex: Int,
    onSelectMode: (Int) -> Unit,
    a4: Int,
    onA4: (Int) -> Unit,
    tuning: Tuning?,
    display: com.tuner.gtuner.pitch.TunerDisplay,
    lockedString: Int,
    tunedStrings: Set<Int>,
    inTune: Boolean,
    onSelectString: (Int) -> Unit,
) {
    TuningSelector(modeNames, selectedIndex, onSelectMode)
    Spacer(Modifier.height(12.dp))
    CalibrationRow(a4, onA4)
    if (tuning != null) {
        Spacer(Modifier.height(20.dp))
        StringSelector(
            tuning = tuning,
            activeIndex = display.stringIndex,
            lockedIndex = lockedString,
            tuned = tunedStrings,
            activeInTune = inTune,
            onSelect = onSelectString,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TuningSelector(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        TextField(
            value = options[selectedIndex],
            onValueChange = {},
            readOnly = true,
            label = { Text("Tuning") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(i)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Reference-pitch calibration. Tap the value to reset to 440 Hz. */
@Composable
private fun CalibrationRow(a4: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onChange(a4 - 1) }) { Text("–", fontSize = 24.sp) }
        Text(
            text = "A4 = $a4 Hz",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable { onChange(440) }
                .padding(horizontal = 8.dp),
        )
        TextButton(onClick = { onChange(a4 + 1) }) { Text("+", fontSize = 24.sp) }
    }
}

/** Row of string targets; tap to lock. Active highlights, in-tune/done turn green with a check. */
@Composable
private fun StringSelector(
    tuning: Tuning,
    activeIndex: Int,
    lockedIndex: Int,
    tuned: Set<Int>,
    activeInTune: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tuning.notes.forEachIndexed { i, note ->
                val isActive = i == activeIndex
                val isTuned = i in tuned
                val isLocked = i == lockedIndex
                val background = when {
                    isTuned -> InTuneGreen
                    isActive && activeInTune -> InTuneGreen
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val foreground = when {
                    isTuned || isActive -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(background)
                        .then(
                            if (isLocked) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isTuned) "✓" else note,
                        color = foreground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (lockedIndex in tuning.notes.indices) {
                "Locked: ${tuning.notes[lockedIndex]} · tap again to auto-detect"
            } else {
                "Tap a string to lock onto it for precision"
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

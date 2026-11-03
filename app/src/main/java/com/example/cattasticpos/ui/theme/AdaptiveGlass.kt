package com.example.cattasticpos.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.cattasticpos.ui.adaptive.BionicHaptic
import com.example.cattasticpos.ui.adaptive.iOSSpring
import com.example.cattasticpos.ui.adaptive.rememberBionicHaptic
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.max
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ObsidianPalette {
    val Canvas = Color(0xFF050507)
    val CanvasAlt = Color(0xFF0B0B0C)
    val GlassFill = Color.White.copy(alpha = 0.04f)
    val BodyMuted = Color.White.copy(alpha = 0.7f)
    val EmeraldGlow = Color(0xFF10B981)
    val GoldGlow = Color(0xFFD4AF37)
    val Handle = Color.White.copy(alpha = 0.15f)
    val GlassRadius = 22.dp
}

object AlabasterPalette {
    val Canvas = Color(0xFFF8F9FA)
    val CanvasAlt = Color(0xFFFFFFFF)
    val Heading = Color(0xFF1A1A1E)
    val GlassFill = Color.White.copy(alpha = 0.65f)
    val BodyMuted = Color(0xFF3B3B3B).copy(alpha = 0.85f)
    val MintGlow = Color(0xFFA7F3D0)
    val RoseGoldGlow = Color(0xFFFCA5A5)
    val Handle = Color.Black.copy(alpha = 0.12f)
    val RingBorder = Color.Black.copy(alpha = 0.06f)
    val GlassRadius = 22.dp
}

fun alabasterSpecularBorderBrush(): Brush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.9f),
        Color.Black.copy(alpha = 0.06f),
        Color.White.copy(alpha = 0.4f)
    )
)

@Composable
fun isAdaptiveDarkTheme(): Boolean = isSystemInDarkTheme()

fun specularBorderBrush(): Brush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.15f),
        Color.Transparent,
        Color.Black.copy(alpha = 0.2f)
    )
)

fun neonSelectionBrush(accent: Color): Brush = Brush.linearGradient(
    colors = listOf(
        accent.copy(alpha = 0.5f),
        accent.copy(alpha = 0.25f),
        accent.copy(alpha = 0.15f)
    )
)

fun adaptiveGlassBrush(darkTheme: Boolean): Brush =
    Brush.verticalGradient(
        colors = if (darkTheme) {
            listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
        } else {
            listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.40f))
        }
    )

@Composable
fun adaptiveGlassFill(darkTheme: Boolean = isAdaptiveDarkTheme()): Color =
    if (darkTheme) ObsidianPalette.GlassFill else Color.White.copy(alpha = 0.52f)

@Composable
fun adaptiveBodyMuted(darkTheme: Boolean = isAdaptiveDarkTheme()): Color =
    if (darkTheme) ObsidianPalette.BodyMuted else AlabasterPalette.BodyMuted

@Composable
fun adaptiveGlassContentColor(darkTheme: Boolean = isAdaptiveDarkTheme()): Color =
    if (darkTheme) Color.White else AlabasterPalette.Heading

@Composable
fun adaptiveGlassRadius(darkTheme: Boolean = isAdaptiveDarkTheme()): Dp =
    if (darkTheme) ObsidianPalette.GlassRadius else AlabasterPalette.GlassRadius

@Composable
fun adaptiveTypography(darkTheme: Boolean = isAdaptiveDarkTheme()): Typography {
    val base = Typography()
    val headingColor = if (darkTheme) Color.White else AlabasterPalette.Heading
    val bodyColor = adaptiveBodyMuted(darkTheme)
    return Typography(
        displayLarge = base.displayLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = headingColor
        ),
        headlineLarge = base.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = headingColor
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = headingColor
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = headingColor
        ),
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            color = headingColor
        ),
        titleSmall = base.titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = headingColor
        ),
        bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Medium, color = bodyColor),
        bodyMedium = base.bodyMedium.copy(fontWeight = FontWeight.Medium, color = bodyColor),
        bodySmall = base.bodySmall.copy(
            fontWeight = FontWeight.Medium,
            color = bodyColor.copy(alpha = if (darkTheme) 0.85f else 0.9f)
        ),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, color = bodyColor),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, color = bodyColor),
        labelSmall = base.labelSmall.copy(
            fontWeight = FontWeight.Medium,
            color = bodyColor.copy(alpha = if (darkTheme) 0.75f else 0.8f)
        )
    )
}


@Composable
private fun AmbientGlowBlob(
    modifier: Modifier = Modifier,
    color: Color,
    peakAlpha: Float
) {
    Box(
        modifier = modifier.graphicsLayer { clip = false }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val radius = max(size.width, size.height) * 0.58f
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = peakAlpha),
                                color.copy(alpha = peakAlpha * 0.35f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = radius
                        )
                    )
                }
        )
    }
}

@Composable
fun AdaptiveAmbientGlows(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isAdaptiveDarkTheme(),
    compactLayout: Boolean = false
) {
    val accentPrimary = MaterialTheme.colorScheme.primary
    val accentSecondary = MaterialTheme.colorScheme.secondary

    Box(modifier = modifier.graphicsLayer { clip = false }) {
        if (darkTheme) {
            AmbientGlowBlob(
                modifier = Modifier
                    .size(360.dp)
                    .offset(x = (-120).dp, y = (-80).dp),
                color = accentPrimary,
                peakAlpha = 0.08f
            )
            if (!compactLayout) {
                AmbientGlowBlob(
                    modifier = Modifier
                        .size(420.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 140.dp, y = 100.dp),
                    color = accentSecondary,
                    peakAlpha = 0.06f
                )
            }
            AmbientGlowBlob(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-40).dp, y = 80.dp),
                color = accentPrimary,
                peakAlpha = 0.07f
            )
        } else {
            AmbientGlowBlob(
                modifier = Modifier
                    .size(340.dp)
                    .offset(x = (-100).dp, y = (-60).dp),
                color = accentPrimary,
                peakAlpha = 0.12f
            )
            if (!compactLayout) {
                AmbientGlowBlob(
                    modifier = Modifier
                        .size(400.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 120.dp, y = 90.dp),
                    color = accentSecondary,
                    peakAlpha = 0.10f
                )
            }
            AmbientGlowBlob(
                modifier = Modifier
                    .size(280.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-50).dp, y = 70.dp),
                color = accentPrimary,
                peakAlpha = 0.10f
            )
        }
    }
}

@Composable
fun AdaptiveSheetHandle(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isAdaptiveDarkTheme()
) {
    val handleColor = if (darkTheme) ObsidianPalette.Handle else AlabasterPalette.Handle
    Box(
        modifier = modifier
            .width(32.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(handleColor)
    )
}

@Composable
fun AdaptiveGlassSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
    darkTheme: Boolean = isAdaptiveDarkTheme(),
    cornerRadius: Dp = adaptiveGlassRadius(darkTheme),
    dialogMode: Boolean = false,
    surfaceAlpha: Float? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val effectiveRadius = if (dialogMode) 24.dp else cornerRadius
    val shape = RoundedCornerShape(effectiveRadius)
    val glassBrush = adaptiveGlassBrush(darkTheme)
    val fillBrush: Brush? = when {
        selected -> null
        dialogMode && surfaceAlpha != null -> null
        else -> glassBrush
    }
    val fillColor: Color? = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        dialogMode && surfaceAlpha != null -> MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha)
        else -> null
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed) 0.96f else 1f,
        animationSpec = iOSSpring,
        label = "glassCardScale"
    )
    val performHaptic = rememberBionicHaptic()
    val clickModifier = if (onClick != null) {
        Modifier
            .scale(pressScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    performHaptic(BionicHaptic.Selection)
                    onClick()
                }
            )
    } else {
        Modifier
    }

    val surfaceModifier = if (dialogMode) {
        Modifier.border(
            width = 1.dp,
            color = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
            shape = shape
        )
    } else if (darkTheme) {
        Modifier.border(width = 1.dp, brush = if (selected) neonSelectionBrush(accent) else specularBorderBrush(), shape = shape)
    } else if (selected) {
        Modifier.border(width = 1.dp, brush = neonSelectionBrush(accent), shape = shape)
    } else {
        Modifier.border(width = 1.dp, color = Color.Black.copy(alpha = 0.05f), shape = shape)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (fillBrush != null) {
                    Modifier.background(fillBrush, shape)
                } else {
                    Modifier.background(fillColor!!, shape)
                }
            )
            .then(surfaceModifier)
            .then(clickModifier),
        content = content
    )
}

@Composable
fun AdaptiveGlassCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dialogMode: Boolean = false,
    surfaceAlpha: Float? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    AdaptiveGlassSurface(
        modifier = modifier,
        selected = selected,
        dialogMode = dialogMode,
        surfaceAlpha = surfaceAlpha,
        onClick = onClick,
        content = content
    )
}

@Composable
fun AdaptiveGlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    surfaceAlpha: Float? = null,
    fixedFooter: Boolean = false,
    contentMaxHeight: Dp = 480.dp,
    contentPadding: Dp = 24.dp,
    /** Lets tall content scroll so the buttons stay on screen; for content with no scrolling list of its own. */
    scrollContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val textColor = adaptiveGlassContentColor()
    val hasFooter = confirmButton != null || dismissButton != null
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AdaptiveGlassCard(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .then(
                    if (fixedFooter && hasFooter) {
                        Modifier.fillMaxHeight(0.9f)
                    } else {
                        Modifier.wrapContentHeight()
                    }
                ),
            dialogMode = true,
            surfaceAlpha = surfaceAlpha
        ) {
            Column(
                modifier = Modifier
                    .then(
                        if (fixedFooter && hasFooter) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.wrapContentHeight()
                        }
                    )
                    .padding(contentPadding)
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides textColor,
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(color = textColor)
                ) {
                    if (title != null) {
                        Box(modifier = Modifier.fillMaxWidth()) { title() }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (fixedFooter && hasFooter) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .heightIn(max = contentMaxHeight)
                                .verticalScroll(rememberScrollState())
                        ) {
                            content()
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            dismissButton?.invoke()
                            if (dismissButton != null && confirmButton != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            confirmButton?.invoke()
                        }
                    } else {
                        if (scrollContent) {
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                content()
                            }
                        } else {
                            content()
                        }
                        if (hasFooter) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                dismissButton?.invoke()
                                if (dismissButton != null && confirmButton != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                confirmButton?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ObsidianAmbientGlows(modifier: Modifier = Modifier) = AdaptiveAmbientGlows(modifier)

@Composable
fun ObsidianSheetHandle(modifier: Modifier = Modifier) = AdaptiveSheetHandle(modifier)

@Composable
fun ObsidianGlassSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
    cornerRadius: Dp = ObsidianPalette.GlassRadius,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) = AdaptiveGlassSurface(modifier, selected, accent, cornerRadius = cornerRadius, onClick = onClick, content = content)

@Composable
fun ObsidianGlassCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) = AdaptiveGlassCard(modifier = modifier, selected = selected, onClick = onClick, content = content)

package com.example.cattasticpos.ui.adaptive

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.cattasticpos.R
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState

@Stable
class CollapsingHeaderState internal constructor(
    val nestedScrollConnection: NestedScrollConnection,
    val collapseProgress: () -> Float,
    private val resetCollapseOffset: () -> Unit
) {
    fun resetCollapse() {
        resetCollapseOffset()
    }
}

@Composable
fun rememberCollapsingHeaderState(collapseRange: Dp = 120.dp): CollapsingHeaderState {
    val density = LocalDensity.current
    val collapseRangePx = with(density) { collapseRange.toPx() }
    val scrollOffset = remember { mutableFloatStateOf(0f) }

    val collapseProgress = remember(collapseRangePx) {
        derivedStateOf {
            (scrollOffset.floatValue / collapseRangePx).coerceIn(0f, 1f)
        }
    }

    val connection = remember(collapseRangePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && scrollOffset.floatValue > 0f) {
                    val consumed = available.y.coerceAtMost(scrollOffset.floatValue)
                    scrollOffset.floatValue -= consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y < 0f && scrollOffset.floatValue < collapseRangePx) {
                    val consumedY = (-available.y).coerceAtMost(collapseRangePx - scrollOffset.floatValue)
                    scrollOffset.floatValue += consumedY
                    return Offset(0f, -consumedY)
                }
                return Offset.Zero
            }
        }
    }

    return remember(connection, collapseProgress) {
        CollapsingHeaderState(
            nestedScrollConnection = connection,
            collapseProgress = { collapseProgress.value },
            resetCollapseOffset = { scrollOffset.floatValue = 0f }
        )
    }
}

fun Modifier.collapsingNestedScroll(state: CollapsingHeaderState): Modifier =
    nestedScroll(state.nestedScrollConnection)

@Composable
fun BrewNiCatBrandIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.logo),
        contentDescription = "Brew ni Cat Brand Icon",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}

// Scaffold's content-padding is intentionally ignored: this scaffold sets contentWindowInsets to
// zero and applies safe-drawing insets itself via windowInsetsPadding below, handing the content a
// computed top inset for the collapsing header. Bottom chrome is owned by each screen.
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun CollapsingGlassScaffold(
    title: String,
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    headerState: CollapsingHeaderState = rememberCollapsingHeaderState(),
    showBrandWordmark: Boolean = false,
    showCollapsedToolbarTitle: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    // Edge-to-edge: bottom inset is handled by screen chrome (e.g. the order bar), not here.
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    content: @Composable (PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val largeTitleShiftPx = with(density) { 28.dp.toPx() }
    val rawProgress = headerState.collapseProgress()
    val performHaptic = rememberBionicHaptic()

    // snapshotFlow keeps this to ONE long-lived coroutine; keying a LaunchedEffect on the
    // progress value itself restarted a coroutine every frame while scrolling.
    LaunchedEffect(headerState) {
        var previous = headerState.collapseProgress()
        snapshotFlow { headerState.collapseProgress() }.collect { progress ->
            val crossedClosed = previous < 0.95f && progress >= 0.95f
            val crossedOpen = previous > 0.05f && progress <= 0.05f
            if (crossedClosed || crossedOpen) {
                performHaptic(BionicHaptic.Snap)
            }
            previous = progress
        }
    }

    val collapseProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = iOSSpringSpec,
        label = "collapseProgress"
    )

    val largeTitleAlpha by animateFloatAsState(
        targetValue = 1f - collapseProgress,
        animationSpec = iOSSpringSpec,
        label = "largeTitleAlpha"
    )
    val barHeight = 56.dp
    val largeTitleBlockHeight = 48.dp
    val topContentInset by animateDpAsState(
        targetValue = barHeight + largeTitleBlockHeight * (1f - collapseProgress),
        animationSpec = iOSSpringDp,
        label = "topContentInset"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = snackbarHost,
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = contentWindowInsets
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
            ) {
                content(PaddingValues(top = topContentInset))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .clipToBounds()
                .zIndex(2f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
            ) {
                if (collapseProgress > 0f) {
                    // Solid scrim instead of Haze blur — Haze 1.1.x crashes when backgroundColor
                    // is missing during progressive redraws (e.g. add-to-cart on some devices).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(
                                    alpha = (0.88f * collapseProgress).coerceIn(0f, 1f)
                                )
                            )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.height(barHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        navigationIcon()
                    }
                    if (showCollapsedToolbarTitle && collapseProgress > 0.01f) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                modifier = Modifier.graphicsLayer { alpha = collapseProgress },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }

            if (showBrandWordmark) {
                GlassWordmark(
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(largeTitleBlockHeight * (1f - collapseProgress))
                        .background(Color.Transparent)
                        .padding(horizontal = 16.dp)
                        .graphicsLayer {
                            alpha = largeTitleAlpha
                            translationY = -collapseProgress * largeTitleShiftPx
                            clip = true
                        }
                )
            } else {
                Text(
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(largeTitleBlockHeight * (1f - collapseProgress))
                        .background(Color.Transparent)
                        .padding(horizontal = 16.dp)
                        .graphicsLayer {
                            alpha = largeTitleAlpha
                            translationY = -collapseProgress * largeTitleShiftPx
                            clip = true
                        },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

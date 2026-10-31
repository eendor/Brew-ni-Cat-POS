package com.example.cattasticpos.ui.adaptive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun screenDepth(screen: String): Int = when (screen) {
    "dashboard" -> 0
    "pin_history", "inventory" -> 1
    "history", "pin_menu_editor" -> 2
    "menu_editor" -> 3
    else -> 0
}

fun isPushNavigation(from: String, to: String): Boolean =
    screenDepth(to) > screenDepth(from)

@Composable
fun ParallaxNavHost(
    targetScreen: String,
    isPushing: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (screen: String) -> Unit
) {
    AnimatedContent(
        targetState = targetScreen,
        modifier = modifier,
        transitionSpec = {
            if (isPushing) pushTransition() else popTransition()
        },
        label = "ParallaxNav"
    ) { screen ->
        val isExiting = screen != targetScreen
        val popShadow = if (!isPushing && isExiting) 16.dp else 0.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(popShadow)
                .drawBehind {
                    if (!isPushing && isExiting) {
                        drawRect(
                            color = Color.Black.copy(alpha = 0.15f),
                            size = androidx.compose.ui.geometry.Size(6.dp.toPx(), size.height)
                        )
                    }
                }
        ) {
            content(screen)
        }
    }
}

private fun AnimatedContentTransitionScope<String>.pushTransition(): ContentTransform {
    return (slideInHorizontally(
        animationSpec = iOSNavigationSpring,
        initialOffsetX = { fullWidth -> fullWidth }
    ) + fadeIn(animationSpec = iOSSpring)) togetherWith
        (slideOutHorizontally(
            animationSpec = iOSNavigationSpring,
            targetOffsetX = { fullWidth -> -fullWidth / 3 }
        ) + fadeOut(animationSpec = iOSSpring, targetAlpha = 0.7f))
}

private fun AnimatedContentTransitionScope<String>.popTransition(): ContentTransform {
    return (slideInHorizontally(
        animationSpec = iOSNavigationSpring,
        initialOffsetX = { fullWidth -> -fullWidth / 3 }
    ) + fadeIn(animationSpec = iOSSpring)) togetherWith
        (slideOutHorizontally(
            animationSpec = iOSNavigationSpring,
            targetOffsetX = { fullWidth -> fullWidth }
        ) + fadeOut(animationSpec = iOSSpring))
}

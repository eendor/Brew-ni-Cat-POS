package com.example.cattasticpos.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cattasticpos.ui.adaptive.AdaptiveScaffold
import com.example.cattasticpos.ui.adaptive.AdaptiveTopAppBar
import com.example.cattasticpos.ui.adaptive.LocalCupertinoColors
import com.example.cattasticpos.ui.adaptive.iOSSpringSpec
import com.example.cattasticpos.ui.components.unstyled.PosPrimaryButton
import com.example.cattasticpos.ui.config.PinGateViewModel
import com.example.cattasticpos.ui.icons.FluentIcon
import com.example.cattasticpos.ui.icons.FluentIcons
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinScreen(
    onPinSuccess: () -> Unit,
    onCancel: () -> Unit,
    subtitle: String = "Enter your PIN to view order history",
    pinGateViewModel: PinGateViewModel = viewModel(factory = PinGateViewModel.Factory)
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cupertino = LocalCupertinoColors.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AdaptiveScaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = "Admin Access",
                largeTitle = false,
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            FluentIcon(
                                imageVector = FluentIcons.ArrowLeft,
                                contentDescription = "Cancel",
                                tint = cupertino.accent,
                                size = 24.dp
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Enter PIN",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) { index ->
                        PinDotIndicator(
                            filled = index < pin.length,
                            isError = isError,
                            accentColor = cupertino.accent,
                            emptyColor = cupertino.fill
                        )
                    }
                }

                BasicTextField(
                    value = pin,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        if (digits.length <= 4) {
                            pin = digits
                            isError = false
                        }
                    },
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.size(0.dp)) {
                            innerTextField()
                        }
                    }
                )
            }

            if (isError) {
                Text(
                    text = "Incorrect PIN",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            PosPrimaryButton(
                onClick = {
                    scope.launch {
                        isVerifying = true
                        val verified = pinGateViewModel.verifyPin(pin)
                        isVerifying = false
                        if (verified) {
                            onPinSuccess()
                        } else {
                            isError = true
                            pin = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = pin.length == 4 && !isVerifying
            ) {
                Text(
                    text = if (isVerifying) "Verifying..." else "Unlock",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PinDotIndicator(
    filled: Boolean,
    isError: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    emptyColor: androidx.compose.ui.graphics.Color
) {
    val scale by animateFloatAsState(
        targetValue = if (filled) 1.15f else 1f,
        animationSpec = iOSSpringSpec,
        label = "pinDotScale"
    )
    val dotColor by animateColorAsState(
        targetValue = when {
            isError && filled -> MaterialTheme.colorScheme.error
            filled -> accentColor
            else -> emptyColor
        },
        animationSpec = spring<Color>(dampingRatio = 0.82f, stiffness = 380f),
        label = "pinDotColor"
    )

    Box(
        modifier = Modifier
            .size(14.dp)
            .scale(scale)
            .background(dotColor, CircleShape)
    )
}

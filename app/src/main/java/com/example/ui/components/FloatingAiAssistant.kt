package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.FinanceViewModel
import com.example.service.ChatMessage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FloatingAiAssistant(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAiThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()

    var isSheetOpen by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Drag positions state
    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
    var isInitialized by remember { mutableStateOf(false) }

    var parentWidth by remember { mutableStateOf(0) }
    var parentHeight by remember { mutableStateOf(0) }

    val buttonSize = 56.dp
    val buttonSizePx = with(density) { buttonSize.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }

    // Initialize position relative to parent size once available
    LaunchedEffect(parentWidth, parentHeight) {
        if (parentWidth > 0 && parentHeight > 0 && !isInitialized) {
            animX.snapTo(parentWidth - buttonSizePx - marginPx)
            animY.snapTo(parentHeight - buttonSizePx - marginPx - 180f) // Keep clear of navigation or bottom tabs
            isInitialized = true
        }
    }

    // Gentle pulsing effect for the fintech glow halo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                parentWidth = size.width
                parentHeight = size.height
            }
    ) {
        // 1. Always Visible Floating AI Bubble (if parent is measured)
        if (isInitialized && !isSheetOpen) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(animX.value.roundToInt(), animY.value.roundToInt()) }
                    .size(buttonSize)
                    .testTag("floating_ai_assistant_bubble")
            ) {
                // Glow Halo Ring Behind
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF06B6D4).copy(alpha = pulseAlpha), 
                                        Color(0xFF3B82F6).copy(alpha = 0f)
                                    )
                                )
                            )
                        }
                )

                // Main Circular Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(12.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0891B2), Color(0xFF2563EB))
                            )
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(Color(0xFF22D3EE), Color(0xFF818CF8), Color(0xFF22D3EE))
                            ),
                            shape = CircleShape
                        )
                        .pointerInput(parentWidth, parentHeight) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var totalDragOffset = androidx.compose.ui.geometry.Offset.Zero
                                var isDragStarted = false
                                drag(down.id) { change ->
                                    val dragAmount = change.positionChange()
                                    if (!isDragStarted) {
                                        totalDragOffset += dragAmount
                                        if (totalDragOffset.getDistance() > 10f) {
                                            isDragStarted = true
                                        }
                                    }
                                    if (isDragStarted) {
                                        change.consume()
                                        val newX = animX.value + dragAmount.x
                                        val newY = animY.value + dragAmount.y
                                        val clampedX = newX.coerceIn(marginPx, parentWidth - buttonSizePx - marginPx)
                                        val clampedY = newY.coerceIn(marginPx, parentHeight - buttonSizePx - marginPx - 100f)
                                        coroutineScope.launch {
                                            animX.snapTo(clampedX)
                                            animY.snapTo(clampedY)
                                        }
                                    }
                                }
                                if (!isDragStarted) {
                                    isSheetOpen = true
                                } else {
                                    val middle = parentWidth / 2f
                                    val targetX = if (animX.value < middle) marginPx else parentWidth - buttonSizePx - marginPx
                                    coroutineScope.launch {
                                        animX.animateTo(
                                            targetValue = targetX,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Ask AI Assistant Quick Launch",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // 2. Beautiful Compact Sliding Bottom Sheet Overlay
        AnimatedVisibility(
            visible = isSheetOpen,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            // Semi-Transparent Scrim Backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { isSheetOpen = false }
            )
        }

        AnimatedVisibility(
            visible = isSheetOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CustomBottomSheetContent(
                viewModel = viewModel,
                chatMessages = chatMessages,
                isAiThinking = isAiThinking,
                onClose = { isSheetOpen = false }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CustomBottomSheetContent(
    viewModel: FinanceViewModel,
    chatMessages: List<ChatMessage>,
    isAiThinking: Boolean,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Autoscroll logic - robust and safe for UI resizing
    val lastIndex = remember(chatMessages.size, isAiThinking) {
        val idx = chatMessages.size - 1 + (if (isAiThinking) 1 else 0)
        maxOf(0, idx)
    }
    LaunchedEffect(lastIndex) {
        if (chatMessages.isNotEmpty() || isAiThinking) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    val suggestedQuestions = listOf(
        "Siapa belum bayar kas?",
        "Saldo kas tinggal berapa?",
        "Pengeluaran terbesar?",
        "Apakah kas bulan ini aman?"
    )

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .fillMaxHeight(0.94f) // Sized perfectly to nearly full screen (94%) for active dialog in premium fintech styling
            .imePadding() // Keyboard alignment: resizes the bottom sheet container perfectly when keyb is opened
            .navigationBarsPadding()
            .testTag("ai_assistant_bottom_sheet")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Drag handle and standard header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
            }

            // Title Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Micro glow indicator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22D3EE))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Asisten Ask AI KB SPASI",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Empty chat/trash indicator
                    IconButton(
                        onClick = { viewModel.clearChatHistory() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Kosongkan Chat",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Done/Close button
                    TextButton(onClick = onClose) {
                        Text("Selesai", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

            // Chat body box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f))
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chatMessages, key = { it.id }) { message ->
                        BottomSheetMessageBubble(message = message)
                    }

                    if (isAiThinking) {
                        item {
                            BottomSheetThinkingBubble()
                        }
                    }
                }
            }

            // Quick suggestion chips
            if (!isAiThinking) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestedQuestions.forEach { question ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .clickable {
                                        textInput = ""
                                        viewModel.sendQuestionToAi(question)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = question,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Text input container with keyboard buffers
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { 
                            Text(
                                "Tanya asisten kas...", 
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            ) 
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("modal_ai_input"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textInput.isNotBlank() && !isAiThinking) {
                                    viewModel.sendQuestionToAi(textInput.trim())
                                    textInput = ""
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank() && !isAiThinking) {
                                viewModel.sendQuestionToAi(textInput.trim())
                                textInput = ""
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (textInput.isBlank() || isAiThinking) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Kirim",
                            tint = if (textInput.isBlank() || isAiThinking) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomSheetMessageBubble(message: ChatMessage) {
    val bubbleColor = if (message.isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val alignment = if (message.isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isUser) 16.dp else 2.dp,
                        bottomEnd = if (message.isUser) 2.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                fontSize = 14.sp,
                color = textColor,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun BottomSheetThinkingBubble() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "AI Berpikir",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                
                val infiniteTransition = rememberInfiniteTransition(label = "pulse_dots")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
    }
}

package com.yuanman.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A consistent, reversible swipe action row. Swiping only reveals the delete action;
 * deletion is triggered by an explicit tap on the action area.
 */
@Composable
fun SwipeRevealDeleteItem(
    itemKey: Long,
    openKey: Long?,
    onOpen: (Long?) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val actionWidth = 96.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }

    LaunchedEffect(openKey) {
        if (openKey != itemKey && offset.value != 0f) {
            offset.animateTo(0f, animationSpec = tween(180))
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier
                    .width(actionWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable {
                        onOpen(null)
                        scope.launch {
                            offset.animateTo(0f, animationSpec = tween(180))
                        }
                        onDelete()
                    }
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(actionWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offset.snapTo(
                                    (offset.value + dragAmount).coerceIn(-actionWidthPx, 0f)
                                )
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                val target = if (offset.value <= -actionWidthPx * 0.35f) {
                                    -actionWidthPx
                                } else {
                                    0f
                                }
                                onOpen(if (target == 0f) null else itemKey)
                                offset.animateTo(target, animationSpec = tween(180))
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offset.animateTo(0f, animationSpec = tween(180))
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

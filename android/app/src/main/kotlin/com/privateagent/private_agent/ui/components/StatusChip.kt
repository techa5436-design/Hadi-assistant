package com.privateagent.private_agent.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privateagent.private_agent.model.TaskStatus
import com.privateagent.private_agent.ui.theme.DangerRed
import com.privateagent.private_agent.ui.theme.SuccessGreen
import com.privateagent.private_agent.ui.theme.WarningAmber

@Composable
fun StatusChip(
    label: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(color.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.4f), shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.W600,
            color = color
        )
    }
}

@Composable
fun TaskStatusChip(status: TaskStatus, modifier: Modifier = Modifier) {
    when (status) {
        TaskStatus.SUCCESS -> StatusChip(
            label = "Success",
            color = SuccessGreen,
            icon = Icons.Default.CheckCircle,
            modifier = modifier
        )
        TaskStatus.FAILED -> StatusChip(
            label = "Failed",
            color = DangerRed,
            icon = Icons.Default.Error,
            modifier = modifier
        )
        TaskStatus.CANCELLED -> StatusChip(
            label = "Cancelled",
            color = WarningAmber,
            icon = Icons.Default.StopCircle,
            modifier = modifier
        )
    }
}

@Composable
fun ServiceStatusDot(enabled: Boolean, modifier: Modifier = Modifier) {
    val color = if (enabled) SuccessGreen else DangerRed
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .alpha(if (enabled) alpha else 1.0f)
            .background(color, shape = CircleShape)
    )
}

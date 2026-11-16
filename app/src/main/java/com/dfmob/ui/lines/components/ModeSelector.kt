package com.dfmob.ui.lines.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.TransitModeFilter
import com.dfmob.ui.theme.TransitBlue
import com.dfmob.ui.theme.TransitGreen

@Composable
fun ModeSelector(
    selectedMode: TransitModeFilter,
    onModeSelected: (TransitModeFilter) -> Unit,
    busCount: Int,
    metroCount: Int,
    modifier: Modifier = Modifier
) {
    val containerShape = RoundedCornerShape(14.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(containerShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = containerShape
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = containerShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeTab(
                title = "Ônibus ($busCount)",
                dotColor = TransitGreen,
                isSelected = selectedMode == TransitModeFilter.BUS,
                modifier = Modifier.weight(1f),
                onClick = { onModeSelected(TransitModeFilter.BUS) }
            )

            ModeTab(
                title = "Metrô-DF ($metroCount)",
                dotColor = TransitBlue,
                isSelected = selectedMode == TransitModeFilter.METRO,
                modifier = Modifier.weight(1f),
                onClick = { onModeSelected(TransitModeFilter.METRO) }
            )
        }
    }
}

@Composable
private fun ModeTab(
    title: String,
    dotColor: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tabShape = RoundedCornerShape(10.dp)
    val interactionSource = remember { MutableInteractionSource() }

    val bgColor = if (isSelected) Color(0xFF1E1E1E) else Color.Transparent
    val borderModifier = if (isSelected) {
        Modifier.border(width = 1.dp, color = Color(0xFF333333), shape = tabShape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(tabShape)
            .then(borderModifier)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true),
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

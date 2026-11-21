package com.dfmob.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.TripInsight
import com.dfmob.data.model.TripInsightType
import com.dfmob.ui.theme.TransitGreen

@Composable
fun TripInsightBadge(
    insight: TripInsight,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val (textColor, bgColor, icon) = when (insight.type) {
        TripInsightType.FASTEST -> Triple(
            if (isDarkTheme) TransitGreen else Color(0xFF00883E),
            if (isDarkTheme) Color(0x2200C853) else Color(0x1900883E),
            Icons.Rounded.Bolt
        )
        TripInsightType.DIRECT -> Triple(
            if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1976D2),
            if (isDarkTheme) Color(0x222196F3) else Color(0x191976D2),
            Icons.AutoMirrored.Rounded.ArrowForward
        )
        TripInsightType.LEAST_WALKING -> Triple(
            if (isDarkTheme) Color(0xFFFBBF24) else Color(0xFFD97706),
            if (isDarkTheme) Color(0x26FBBF24) else Color(0x1CD97706),
            Icons.AutoMirrored.Rounded.DirectionsWalk
        )
        TripInsightType.DEPARTING_SOON -> Triple(
            if (isDarkTheme) Color(0xFF34D399) else Color(0xFF059669),
            if (isDarkTheme) Color(0x2834D399) else Color(0x18059669),
            Icons.Rounded.Schedule
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = insight.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp
                ),
                color = textColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

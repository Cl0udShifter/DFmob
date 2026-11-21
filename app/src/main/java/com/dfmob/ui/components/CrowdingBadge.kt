package com.dfmob.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
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
import com.dfmob.data.model.CrowdingCategory
import com.dfmob.data.model.CrowdingInfo
import com.dfmob.ui.theme.TransitGreen

@Composable
fun CrowdingBadge(
    crowding: CrowdingInfo,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    useShortText: Boolean = true
) {
    val (textColor, bgColor, icon) = when (crowding.category) {
        CrowdingCategory.LOW -> Triple(
            if (isDarkTheme) TransitGreen else Color(0xFF00883E),
            if (isDarkTheme) Color(0x2200C853) else Color(0x1900883E),
            Icons.Rounded.Person
        )
        CrowdingCategory.MEDIUM -> Triple(
            if (isDarkTheme) Color(0xFFFBBF24) else Color(0xFFD97706),
            if (isDarkTheme) Color(0x26FBBF24) else Color(0x1CD97706),
            Icons.Rounded.People
        )
        CrowdingCategory.HIGH -> Triple(
            if (isDarkTheme) Color(0xFFF87171) else Color(0xFFDC2626),
            if (isDarkTheme) Color(0x26F87171) else Color(0x1CDC2626),
            Icons.Rounded.Groups
        )
    }

    val text = if (useShortText) crowding.shortLabel else crowding.displayLabel

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bgColor)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = textColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

package com.dfmob.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.LinearProgressIndicator
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
import com.dfmob.ui.theme.TransitGreen
import com.dfmob.updater.UpdateState

@Composable
fun AppUpdateModule(
    updateState: UpdateState,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Aparece somente quando ha update disponivel, baixando ou pronto para instalar
    if (updateState !is UpdateState.UpdateAvailable &&
        updateState !is UpdateState.Downloading &&
        updateState !is UpdateState.ReadyToInstall
    ) {
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = 1.dp,
                color = TransitGreen.copy(alpha = 0.5f),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true),
                enabled = updateState !is UpdateState.Downloading,
                onClick = onInstallUpdate
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (updateState is UpdateState.ReadyToInstall) TransitGreen else Color(0xFFEAB308)
                            )
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = when (updateState) {
                            is UpdateState.Downloading -> "Baixando atualização: ${updateState.progressPercent}%"
                            is UpdateState.ReadyToInstall -> "Atualização pronta para instalação"
                            is UpdateState.UpdateAvailable -> "Nova versão ${updateState.releaseInfo.tagName} disponível"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = when (updateState) {
                        is UpdateState.Downloading -> "${updateState.progressPercent}%"
                        is UpdateState.ReadyToInstall -> "Instalar"
                        is UpdateState.UpdateAvailable -> "Atualizar"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = TransitGreen
                )
            }

            if (updateState is UpdateState.Downloading) {
                LinearProgressIndicator(
                    progress = { updateState.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = TransitGreen,
                    trackColor = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

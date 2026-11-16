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
import androidx.compose.material3.CircularProgressIndicator
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
    currentVersion: String,
    onCheckOrInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = shape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(bounded = true),
                    enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading,
                    onClick = onCheckOrInstallUpdate
                ),
            color = MaterialTheme.colorScheme.surface,
            shape = shape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (updateState) {
                        is UpdateState.Checking -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 2.dp,
                                color = TransitGreen
                            )
                        }

                        is UpdateState.Downloading -> {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(TransitGreen)
                            )
                        }

                        is UpdateState.UpdateAvailable -> {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEAB308))
                            )
                        }

                        is UpdateState.ReadyToInstall -> {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(TransitGreen)
                            )
                        }

                        is UpdateState.Error -> {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                        }

                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(TransitGreen)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = when (updateState) {
                            is UpdateState.Checking -> "Consultando repositório..."
                            is UpdateState.Downloading -> "Baixando: ${updateState.progressPercent}%"
                            is UpdateState.UpdateAvailable -> "Nova versão ${updateState.releaseInfo.tagName}"
                            is UpdateState.ReadyToInstall -> "APK pronto"
                            is UpdateState.Error -> "Falha na conexão"
                            else -> "DFmob v$currentVersion"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = when (updateState) {
                        is UpdateState.Checking -> "Aguarde"
                        is UpdateState.Downloading -> "${updateState.progressPercent}%"
                        is UpdateState.UpdateAvailable -> "Baixar"
                        is UpdateState.ReadyToInstall -> "Instalar"
                        is UpdateState.Error -> "Tentar de novo"
                        else -> "Verificar atualizações"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = TransitGreen
                )
            }
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

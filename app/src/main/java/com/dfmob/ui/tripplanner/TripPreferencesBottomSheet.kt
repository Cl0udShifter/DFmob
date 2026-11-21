package com.dfmob.ui.tripplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.TripPreference

private val SheetTransitGreen = Color(0xFF00A651)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPreferencesBottomSheet(
    sheetState: SheetState,
    currentPreference: TripPreference,
    allowBus: Boolean,
    allowSubway: Boolean,
    onDismissRequest: () -> Unit,
    onApply: (preference: TripPreference, allowBus: Boolean, allowSubway: Boolean) -> Unit,
    onReset: () -> Unit
) {
    var selectedPreference by remember { mutableStateOf(currentPreference) }
    var selectedAllowBus by remember { mutableStateOf(allowBus) }
    var selectedAllowSubway by remember { mutableStateOf(allowSubway) }

    val hasCustomFilters = selectedPreference != TripPreference.FASTEST ||
            !selectedAllowBus ||
            !selectedAllowSubway

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            // Cabecalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preferências de Viagem",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Fechar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SECAO 1: Prioridade da Rota
            Text(
                text = "PRIORIDADE DE ROTA",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(vertical = 4.dp)
            ) {
                PreferenceOptionRow(
                    icon = Icons.Rounded.ElectricBolt,
                    title = "Mais rápida (Recomendada)",
                    subtitle = "Prioriza rotas com o menor tempo total de deslocamento",
                    isSelected = selectedPreference == TripPreference.FASTEST,
                    onClick = { selectedPreference = TripPreference.FASTEST }
                )
                PreferenceOptionRow(
                    icon = Icons.Rounded.SyncAlt,
                    title = "Menos trocas / baldeações",
                    subtitle = "Prioriza linhas diretas e trajetos com menos conexões",
                    isSelected = selectedPreference == TripPreference.LEAST_TRANSFERS,
                    onClick = { selectedPreference = TripPreference.LEAST_TRANSFERS }
                )
                PreferenceOptionRow(
                    icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                    title = "Caminhar menos",
                    subtitle = "Prioriza paradas mais próximas e menores percursos a pé",
                    isSelected = selectedPreference == TripPreference.LEAST_WALKING,
                    onClick = { selectedPreference = TripPreference.LEAST_WALKING }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECAO 2: Modais de Transporte
            Text(
                text = "MEIOS DE TRANSPORTE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(vertical = 4.dp)
            ) {
                ModalToggleRow(
                    icon = Icons.Rounded.DirectionsBus,
                    title = "Ônibus",
                    subtitle = "Linhas convencionais, circulares, troncais e BRT",
                    isChecked = selectedAllowBus,
                    onCheckedChange = { checked ->
                        // Nao permite desmarcar ambos
                        if (!checked && !selectedAllowSubway) return@ModalToggleRow
                        selectedAllowBus = checked
                    }
                )
                ModalToggleRow(
                    icon = Icons.Rounded.DirectionsSubway,
                    title = "Metrô-DF",
                    subtitle = "Linha Verde e Linha Laranja",
                    isChecked = selectedAllowSubway,
                    onCheckedChange = { checked ->
                        // Nao permite desmarcar ambos
                        if (!checked && !selectedAllowBus) return@ModalToggleRow
                        selectedAllowSubway = checked
                    }
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Botoes de Acao (Redefinir e Aplicar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasCustomFilters) {
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Redefinir",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Button(
                    onClick = {
                        onApply(selectedPreference, selectedAllowBus, selectedAllowSubway)
                    },
                    modifier = Modifier.weight(if (hasCustomFilters) 1.5f else 1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SheetTransitGreen)
                ) {
                    Text(
                        text = "Aplicar Preferências",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PreferenceOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) SheetTransitGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.5.sp
                ),
                color = if (isSelected) SheetTransitGreen else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 5.dp else 1.5.dp,
                    color = if (isSelected) SheetTransitGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun ModalToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isChecked) SheetTransitGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.5.sp
                ),
                color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SheetTransitGreen,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

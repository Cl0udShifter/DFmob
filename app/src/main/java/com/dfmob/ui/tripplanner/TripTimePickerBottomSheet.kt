package com.dfmob.ui.tripplanner

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.TripTimeMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val SheetTransitGreen = Color(0xFF2BC958)

data class DayOption(
    val label: String,
    val calendar: Calendar
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripTimePickerBottomSheet(
    sheetState: SheetState,
    initialMode: TripTimeMode,
    initialTimeUtc: Long,
    onDismissRequest: () -> Unit,
    onConfirm: (mode: TripTimeMode, targetTimeUtc: Long) -> Unit
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(initialMode) }

    // Estado do calendario inicial
    val initialCal = remember(initialTimeUtc) {
        Calendar.getInstance().apply { timeInMillis = initialTimeUtc }
    }

    var selectedCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply { timeInMillis = initialTimeUtc })
    }

    val timePickerState = rememberTimePickerState(
        initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCal.get(Calendar.MINUTE),
        is24Hour = true
    )

    var isTextInputMode by remember { mutableStateOf(false) }

    // Gera opcoes rapidas de dias (Hoje, Amanha e proximos 5 dias)
    val dayOptions = remember {
        val list = mutableListOf<DayOption>()
        val cal = Calendar.getInstance()
        val dayOfWeekFormat = SimpleDateFormat("EEE, dd MMM", Locale("pt", "BR"))

        for (i in 0..6) {
            val c = cal.clone() as Calendar
            val label = when (i) {
                0 -> "Hoje"
                1 -> "Amanhã"
                else -> dayOfWeekFormat.format(c.time).replaceFirstChar { it.uppercase() }
            }
            list.add(DayOption(label, c))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Cabecalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Horário da Viagem",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Fechar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Seletor de modo (Agora, Partir as, Chegar ate)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TimeModeTab(
                    title = "Agora",
                    isSelected = selectedMode == TripTimeMode.DEPART_NOW,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedMode = TripTimeMode.DEPART_NOW }
                )
                TimeModeTab(
                    title = "Partir às",
                    isSelected = selectedMode == TripTimeMode.DEPART_AT,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedMode = TripTimeMode.DEPART_AT }
                )
                TimeModeTab(
                    title = "Chegar até",
                    isSelected = selectedMode == TripTimeMode.ARRIVE_BY,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedMode = TripTimeMode.ARRIVE_BY }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (selectedMode == TripTimeMode.DEPART_NOW) {
                // Conteudo informativo para partida imediata
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SheetTransitGreen.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SheetTransitGreen.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = SheetTransitGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Partida Imediata",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "O DFmob buscará as rotas partindo agora com base nos dados de GPS em tempo real.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Seletor de data
                Text(
                    text = "Data",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dayOptions.forEach { option ->
                        val isDaySelected = isSameDay(option.calendar, selectedCalendar)
                        Surface(
                            onClick = {
                                val newCal = selectedCalendar.clone() as Calendar
                                newCal.set(Calendar.YEAR, option.calendar.get(Calendar.YEAR))
                                newCal.set(Calendar.MONTH, option.calendar.get(Calendar.MONTH))
                                newCal.set(Calendar.DAY_OF_MONTH, option.calendar.get(Calendar.DAY_OF_MONTH))
                                selectedCalendar = newCal
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDaySelected) SheetTransitGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isDaySelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isDaySelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (isDaySelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Botao Outra Data via DatePickerDialog
                    Surface(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val newCal = selectedCalendar.clone() as Calendar
                                    newCal.set(Calendar.YEAR, year)
                                    newCal.set(Calendar.MONTH, month)
                                    newCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    selectedCalendar = newCal
                                },
                                selectedCalendar.get(Calendar.YEAR),
                                selectedCalendar.get(Calendar.MONTH),
                                selectedCalendar.get(Calendar.DAY_OF_MONTH)
                            ).apply {
                                datePicker.minDate = System.currentTimeMillis() - 1000
                            }.show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = "Outra data",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Outra data",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Seletor de horario (TimePicker Material 3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Horário",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = { isTextInputMode = !isTextInputMode },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isTextInputMode) Icons.Rounded.Schedule else Icons.Rounded.Keyboard,
                            contentDescription = if (isTextInputMode) "Relógio" else "Teclado",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val pickerColors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        clockDialSelectedContentColor = Color.White,
                        clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                        selectorColor = SheetTransitGreen,
                        timeSelectorSelectedContainerColor = SheetTransitGreen.copy(alpha = 0.15f),
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        timeSelectorSelectedContentColor = SheetTransitGreen,
                        timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface
                    )

                    if (isTextInputMode) {
                        TimeInput(
                            state = timePickerState,
                            colors = pickerColors
                        )
                    } else {
                        TimePicker(
                            state = timePickerState,
                            colors = pickerColors
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Botoes de acao
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Cancelar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        val finalTime = if (selectedMode == TripTimeMode.DEPART_NOW) {
                            System.currentTimeMillis()
                        } else {
                            val cal = selectedCalendar.clone() as Calendar
                            cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            cal.set(Calendar.MINUTE, timePickerState.minute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            cal.timeInMillis
                        }
                        onConfirm(selectedMode, finalTime)
                    },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SheetTransitGreen)
                ) {
                    Text(
                        text = "Aplicar Horário",
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
private fun TimeModeTab(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = if (isSelected) SheetTransitGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

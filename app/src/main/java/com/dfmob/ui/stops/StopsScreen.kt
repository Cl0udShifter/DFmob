package com.dfmob.ui.stops

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.StopArrival
import com.dfmob.data.model.TransitStop
import com.dfmob.data.repository.StopsRepository
import com.dfmob.ui.stops.components.BrasiliaMapCanvas
import com.dfmob.ui.stops.components.FloatingArrivalsDeck
import com.dfmob.ui.theme.TransitGreen

@Composable
fun StopsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val repository = remember { StopsRepository(context) }

    var allStops by remember { mutableStateOf<List<TransitStop>>(emptyList()) }
    var selectedStop by remember { mutableStateOf<TransitStop?>(null) }
    var arrivals by remember { mutableStateOf<List<StopArrival>>(emptyList()) }
    var isLoadingArrivals by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<DFLocation>>(emptyList()) }
    var focusLocation by remember { mutableStateOf<DFLocation?>(null) }

    // Carregamento inicial das paradas
    LaunchedEffect(Unit) {
        allStops = repository.getAllStops()
    }

    // Busca de locais do DF em tempo real ao digitar
    LaunchedEffect(searchQuery) {
        searchResults = if (searchQuery.isNotBlank()) {
            repository.searchLocations(searchQuery)
        } else {
            emptyList()
        }
    }

    // Busca chegadas ao vivo quando uma parada e selecionada
    LaunchedEffect(selectedStop) {
        val stop = selectedStop
        if (stop != null) {
            isLoadingArrivals = true
            arrivals = repository.getArrivalsForStop(stop)
            isLoadingArrivals = false
        } else {
            arrivals = emptyList()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF090B10)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Mapa Interativo com Culling e Zoom Threshold (Moovit pattern)
            BrasiliaMapCanvas(
                stops = allStops,
                selectedStop = selectedStop,
                focusLocation = focusLocation,
                onStopSelected = { stop ->
                    selectedStop = stop
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Barra de Busca Superior Expansivel com sugestoes de locais
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val searchShape = RoundedCornerShape(22.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(searchShape)
                        .border(1.dp, Color(0xFF282A33), searchShape),
                    color = Color(0xF215161A),
                    shape = searchShape,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botao Voltar com seta perfeitamente centralizada no circulo
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22242B))
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Voltar",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Campo de texto de busca
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Buscar endereço ou local no DF...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.5.sp
                                    ),
                                    color = Color(0xFF888B96)
                                )
                            }

                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(TransitGreen),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Limpar busca",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Place,
                                contentDescription = "Localizacao",
                                tint = TransitGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Lista Dropdown com locais sugeridos do DF
                if (searchResults.isNotEmpty()) {
                    val dropdownShape = RoundedCornerShape(16.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(dropdownShape)
                            .border(1.dp, Color(0xFF2C2E38), dropdownShape),
                        color = Color(0xF8181A20),
                        shape = dropdownShape,
                        shadowElevation = 14.dp
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                items = searchResults,
                                key = { it.name }
                            ) { loc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF20222A))
                                        .clickable {
                                            focusLocation = loc
                                            searchQuery = loc.name
                                            searchResults = emptyList()
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Place,
                                        contentDescription = null,
                                        tint = TransitGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = loc.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = loc.description,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp
                                            ),
                                            color = Color(0xFF8C90A0),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Botao Flutuante de Centralizar no Mapa
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xE6181A20))
                    .border(1.dp, Color(0xFF2C2E38), CircleShape)
                    .clickable {
                        focusLocation = DFLocation(
                            name = "Rodoviária do Plano Piloto",
                            description = "Zona Central",
                            latE6 = -15794167,
                            lonE6 = -47882778
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = "Centralizar mapa",
                    tint = TransitGreen,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 4. Painel de Chegadas Flutuante (Deck com 14dp de margem, aberto apenas ao selecionar parada)
            AnimatedVisibility(
                visible = selectedStop != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                selectedStop?.let { stop ->
                    FloatingArrivalsDeck(
                        stop = stop,
                        arrivals = arrivals,
                        isLoading = isLoadingArrivals,
                        onClose = {
                            selectedStop = null
                        }
                    )
                }
            }
        }
    }
}

package com.dfmob.ui.tripplanner

import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.TripPlanFilter
import com.dfmob.data.model.TripPlanItinerary
import com.dfmob.data.model.TripPlanLocation
import com.dfmob.data.model.TripPreference
import com.dfmob.data.model.TripTimeMode
import com.dfmob.data.repository.StopsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ActiveField {
    ORIGIN,
    DESTINATION,
    NONE
}

data class TripPlannerUiState(
    val origin: TripPlanLocation? = null,
    val destination: TripPlanLocation? = null,
    val originQuery: String = "",
    val destinationQuery: String = "",
    val activeField: ActiveField = ActiveField.DESTINATION,
    val searchResults: List<DFLocation> = emptyList(),
    val isSearchingLocations: Boolean = false,
    val timeMode: TripTimeMode = TripTimeMode.DEPART_NOW,
    val targetTimeUtc: Long = System.currentTimeMillis(),
    val preference: TripPreference = TripPreference.FASTEST,
    val itineraries: List<TripPlanItinerary> = emptyList(),
    val isSearchingTrips: Boolean = false,
    val tripSearchError: String? = null,
    val hasSearched: Boolean = false,
    val selectedItinerary: TripPlanItinerary? = null,
    val isPickingOnMap: Boolean = false,
    val mapPickerTargetField: ActiveField = ActiveField.DESTINATION
)

class TripPlannerViewModel(
    private val repository: StopsRepository,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripPlannerUiState())
    val uiState: StateFlow<TripPlannerUiState> = _uiState.asStateFlow()

    private var locationSearchJob: Job? = null
    private var tripSearchJob: Job? = null
    private var liveDeparturesJob: Job? = null

    private var savedOriginText: String = ""
    private var savedDestinationText: String = ""

    init {
        useCurrentLocationForOrigin()
    }

    fun setDestination(dest: DFLocation) {
        if (_uiState.value.origin == null) {
            useCurrentLocationForOrigin()
        }
        val target = TripPlanLocation(
            caption = dest.name,
            latE6 = dest.latE6,
            lonE6 = dest.lonE6,
            isUserLocation = false
        )
        savedDestinationText = dest.name
        _uiState.update {
            it.copy(
                destination = target,
                destinationQuery = dest.name,
                searchResults = emptyList(),
                activeField = ActiveField.NONE
            )
        }
        triggerTripSearchIfReady()
    }

    fun useCurrentLocationForOrigin() {
        val userLoc = getLastKnownLocation()
        if (userLoc != null) {
            val loc = TripPlanLocation(
                caption = "Minha localização",
                latE6 = (userLoc.latitude * 1_000_000).toInt(),
                lonE6 = (userLoc.longitude * 1_000_000).toInt(),
                isUserLocation = true
            )
            savedOriginText = "Minha localização"
            _uiState.update {
                it.copy(
                    origin = loc,
                    originQuery = "Minha localização"
                )
            }
        }
    }

    fun onFieldFocused(field: ActiveField) {
        if (field == ActiveField.ORIGIN) {
            if (_uiState.value.originQuery.isNotBlank()) {
                savedOriginText = _uiState.value.originQuery
            }
            _uiState.update { it.copy(activeField = field, originQuery = "") }
        } else if (field == ActiveField.DESTINATION) {
            if (_uiState.value.destinationQuery.isNotBlank()) {
                savedDestinationText = _uiState.value.destinationQuery
            }
            _uiState.update { it.copy(activeField = field, destinationQuery = "") }
        } else {
            _uiState.update { it.copy(activeField = field) }
        }
    }

    fun onFieldBlurred(field: ActiveField) {
        if (field == ActiveField.ORIGIN) {
            if (_uiState.value.originQuery.isBlank() && savedOriginText.isNotBlank()) {
                _uiState.update { it.copy(originQuery = savedOriginText) }
            }
        } else if (field == ActiveField.DESTINATION) {
            if (_uiState.value.destinationQuery.isBlank() && savedDestinationText.isNotBlank()) {
                _uiState.update { it.copy(destinationQuery = savedDestinationText) }
            }
        }
        _uiState.update { it.copy(activeField = ActiveField.NONE, searchResults = emptyList()) }
    }

    fun onQueryChanged(text: String) {
        val currentField = _uiState.value.activeField
        if (currentField == ActiveField.ORIGIN) {
            _uiState.update { it.copy(originQuery = text, origin = if (text.isBlank()) null else it.origin) }
        } else if (currentField == ActiveField.DESTINATION) {
            _uiState.update { it.copy(destinationQuery = text, destination = if (text.isBlank()) null else it.destination) }
        }

        locationSearchJob?.cancel()
        if (text.trim().length >= 3) {
            locationSearchJob = viewModelScope.launch {
                delay(400)
                _uiState.update { it.copy(isSearchingLocations = true) }
                val results = repository.searchLocations(text.trim())
                _uiState.update { it.copy(searchResults = results, isSearchingLocations = false) }
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), isSearchingLocations = false) }
        }
    }

    fun selectLocationResult(loc: DFLocation) {
        val target = TripPlanLocation(
            caption = loc.name,
            latE6 = loc.latE6,
            lonE6 = loc.lonE6,
            isUserLocation = false
        )
        val currentField = _uiState.value.activeField
        if (currentField == ActiveField.ORIGIN) {
            savedOriginText = loc.name
            _uiState.update {
                it.copy(
                    origin = target,
                    originQuery = loc.name,
                    searchResults = emptyList(),
                    activeField = if (it.destination == null) ActiveField.DESTINATION else ActiveField.NONE
                )
            }
        } else {
            savedDestinationText = loc.name
            _uiState.update {
                it.copy(
                    destination = target,
                    destinationQuery = loc.name,
                    searchResults = emptyList(),
                    activeField = ActiveField.NONE
                )
            }
        }
        triggerTripSearchIfReady()
    }

    fun swapOriginAndDestination() {
        val oldOrig = _uiState.value.origin
        val oldDest = _uiState.value.destination
        val oldOrigQuery = _uiState.value.originQuery
        val oldDestQuery = _uiState.value.destinationQuery

        savedOriginText = oldDestQuery
        savedDestinationText = oldOrigQuery

        _uiState.update {
            it.copy(
                origin = oldDest,
                originQuery = oldDestQuery,
                destination = oldOrig,
                destinationQuery = oldOrigQuery,
                searchResults = emptyList()
            )
        }
        triggerTripSearchIfReady()
    }

    fun useCurrentLocationForActiveField() {
        val userLoc = getLastKnownLocation() ?: return
        val loc = TripPlanLocation(
            caption = "Minha localização",
            latE6 = (userLoc.latitude * 1_000_000).toInt(),
            lonE6 = (userLoc.longitude * 1_000_000).toInt(),
            isUserLocation = true
        )
        val currentField = _uiState.value.activeField
        if (currentField == ActiveField.ORIGIN || currentField == ActiveField.NONE) {
            savedOriginText = "Minha localização"
            _uiState.update {
                it.copy(
                    origin = loc,
                    originQuery = "Minha localização",
                    searchResults = emptyList(),
                    activeField = if (it.destination == null) ActiveField.DESTINATION else ActiveField.NONE
                )
            }
        } else {
            savedDestinationText = "Minha localização"
            _uiState.update {
                it.copy(
                    destination = loc,
                    destinationQuery = "Minha localização",
                    searchResults = emptyList(),
                    activeField = ActiveField.NONE
                )
            }
        }
        triggerTripSearchIfReady()
    }

    fun startMapPicker(targetField: ActiveField) {
        _uiState.update {
            it.copy(
                isPickingOnMap = true,
                mapPickerTargetField = targetField,
                searchResults = emptyList()
            )
        }
    }

    fun cancelMapPicker() {
        _uiState.update { it.copy(isPickingOnMap = false) }
    }

    fun confirmMapPicker(latE6: Int, lonE6: Int, caption: String) {
        val loc = TripPlanLocation(
            caption = caption,
            latE6 = latE6,
            lonE6 = lonE6,
            isUserLocation = false
        )
        val targetField = _uiState.value.mapPickerTargetField
        if (targetField == ActiveField.ORIGIN) {
            savedOriginText = caption
            _uiState.update {
                it.copy(
                    origin = loc,
                    originQuery = caption,
                    isPickingOnMap = false,
                    activeField = if (it.destination == null) ActiveField.DESTINATION else ActiveField.NONE
                )
            }
        } else {
            savedDestinationText = caption
            _uiState.update {
                it.copy(
                    destination = loc,
                    destinationQuery = caption,
                    isPickingOnMap = false,
                    activeField = ActiveField.NONE
                )
            }
        }
        triggerTripSearchIfReady()
    }

    fun setTimeMode(mode: TripTimeMode) {
        _uiState.update {
            it.copy(
                timeMode = mode,
                targetTimeUtc = if (mode == TripTimeMode.DEPART_NOW) System.currentTimeMillis() else it.targetTimeUtc
            )
        }
        triggerTripSearchIfReady()
    }

    fun setCustomTime(hourOfDay: Int, minute: Int) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = _uiState.value.targetTimeUtc
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val newTime = cal.timeInMillis
        _uiState.update {
            it.copy(targetTimeUtc = newTime)
        }
        triggerTripSearchIfReady()
    }

    fun addTimeMinutes(minutes: Int) {
        val now = System.currentTimeMillis()
        val currentTarget = if (_uiState.value.timeMode == TripTimeMode.DEPART_NOW) now else _uiState.value.targetTimeUtc
        val newTarget = currentTarget + (minutes * 60 * 1000L)
        if (newTarget < now && minutes < 0) return

        _uiState.update {
            it.copy(
                timeMode = if (it.timeMode == TripTimeMode.DEPART_NOW) TripTimeMode.DEPART_AT else it.timeMode,
                targetTimeUtc = newTarget
            )
        }
        triggerTripSearchIfReady()
    }

    fun togglePreference(pref: TripPreference) {
        _uiState.update {
            val newPref = if (it.preference == pref) TripPreference.FASTEST else pref
            it.copy(preference = newPref)
        }
        triggerTripSearchIfReady()
    }

    fun selectItinerary(itinerary: TripPlanItinerary?) {
        _uiState.update { it.copy(selectedItinerary = itinerary) }
    }

    fun searchTrips() {
        val origin = _uiState.value.origin ?: return
        val destination = _uiState.value.destination ?: return

        liveDeparturesJob?.cancel()
        tripSearchJob?.cancel()
        tripSearchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearchingTrips = true,
                    tripSearchError = null,
                    hasSearched = true,
                    searchResults = emptyList()
                )
            }
            try {
                val filter = TripPlanFilter(
                    mode = _uiState.value.timeMode,
                    targetTimeUtc = _uiState.value.targetTimeUtc,
                    preference = _uiState.value.preference
                )
                val results = repository.searchTripPlan(origin, destination, filter)
                _uiState.update {
                    it.copy(
                        itineraries = results,
                        isSearchingTrips = false,
                        tripSearchError = if (results.isEmpty()) "Nenhum trajeto encontrado para este horário." else null
                    )
                }
                if (results.isNotEmpty()) {
                    startLiveDeparturesTicker()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        itineraries = emptyList(),
                        isSearchingTrips = false,
                        tripSearchError = "Falha ao calcular rotas: ${e.message ?: "Erro desconhecido"}"
                    )
                }
            }
        }
    }

    private fun startLiveDeparturesTicker() {
        liveDeparturesJob?.cancel()
        liveDeparturesJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                val currentItineraries = _uiState.value.itineraries
                if (currentItineraries.isNotEmpty()) {
                    try {
                        val updated = repository.enrichItinerariesWithDepartures(currentItineraries)
                        _uiState.update { it.copy(itineraries = updated) }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun triggerTripSearchIfReady() {
        if (_uiState.value.origin != null && _uiState.value.destination != null) {
            searchTrips()
        }
    }

    suspend fun reverseGeocode(latE6: Int, lonE6: Int): String {
        return repository.reverseGeocode(latE6, lonE6)
    }

    private fun getLastKnownLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationSearchJob?.cancel()
        tripSearchJob?.cancel()
        liveDeparturesJob?.cancel()
    }
}

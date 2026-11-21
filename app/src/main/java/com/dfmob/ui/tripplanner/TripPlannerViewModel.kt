package com.dfmob.ui.tripplanner

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.TripPlanFilter
import com.dfmob.data.model.TripPlanItinerary
import com.dfmob.data.model.TripPlanLocation
import com.dfmob.data.model.TripPreference
import com.dfmob.data.model.TripTimeMode
import com.dfmob.data.repository.StopsRepository
import com.dfmob.data.service.TripInsightsEvaluator
import kotlinx.coroutines.CancellationException
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
    val allowBus: Boolean = true,
    val allowSubway: Boolean = true,
    val isPreferencesSheetOpen: Boolean = false,
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
    private var fallbackJob: Job? = null

    private var savedOriginText: String = ""
    private var savedDestinationText: String = ""

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            handleFreshLocation(loc)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun startLocationUpdates() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 10f, locationListener)
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 15f, locationListener)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 5000L, 10f, locationListener)
            }
        } catch (_: SecurityException) {}
    }

    private fun stopLocationUpdates() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            lm.removeUpdates(locationListener)
        } catch (_: SecurityException) {}
    }

    init {
        startLocationUpdates()
        useCurrentLocationForOrigin()
    }

    fun refreshUserLocation() {
        startLocationUpdates()
        val userLoc = getBestLocation()
        if (userLoc != null && isLocationFresh(userLoc)) {
            handleFreshLocation(userLoc)
        }
    }

    fun stopListeningLocation() {
        stopLocationUpdates()
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

    fun setOriginAndDestination(orig: TripPlanLocation, dest: TripPlanLocation) {
        startLocationUpdates()
        val userLoc = getBestLocation()
        val isFresh = isLocationFresh(userLoc)
        val finalOrig = if (orig.isUserLocation) {
            if (isFresh && userLoc != null) {
                TripPlanLocation(
                    caption = "Minha localização",
                    latE6 = (userLoc.latitude * 1_000_000).toInt(),
                    lonE6 = (userLoc.longitude * 1_000_000).toInt(),
                    isUserLocation = true
                )
            } else {
                scheduleStaleLocationFallback()
                orig.copy(latE6 = 0, lonE6 = 0)
            }
        } else {
            orig
        }
        val finalDest = if (dest.isUserLocation) {
            if (isFresh && userLoc != null) {
                TripPlanLocation(
                    caption = "Minha localização",
                    latE6 = (userLoc.latitude * 1_000_000).toInt(),
                    lonE6 = (userLoc.longitude * 1_000_000).toInt(),
                    isUserLocation = true
                )
            } else {
                scheduleStaleLocationFallback()
                dest.copy(latE6 = 0, lonE6 = 0)
            }
        } else {
            dest
        }
        savedOriginText = finalOrig.caption
        savedDestinationText = finalDest.caption
        _uiState.update {
            it.copy(
                origin = finalOrig,
                originQuery = finalOrig.caption,
                destination = finalDest,
                destinationQuery = finalDest.caption,
                searchResults = emptyList(),
                activeField = ActiveField.NONE
            )
        }
        triggerTripSearchIfReady()
    }

    fun useCurrentLocationForOrigin() {
        startLocationUpdates()
        val userLoc = getBestLocation()
        val isFresh = isLocationFresh(userLoc)
        val latE6 = if (isFresh && userLoc != null) (userLoc.latitude * 1_000_000).toInt() else 0
        val lonE6 = if (isFresh && userLoc != null) (userLoc.longitude * 1_000_000).toInt() else 0
        val loc = TripPlanLocation(
            caption = "Minha localização",
            latE6 = latE6,
            lonE6 = lonE6,
            isUserLocation = true
        )
        savedOriginText = "Minha localização"
        _uiState.update {
            it.copy(
                origin = loc,
                originQuery = "Minha localização",
                searchResults = emptyList()
            )
        }
        if (isFresh) {
            triggerTripSearchIfReady()
        } else {
            scheduleStaleLocationFallback()
        }
    }

    private var lastActiveSearchField: ActiveField = ActiveField.DESTINATION

    fun onFieldFocused(field: ActiveField) {
        if (field != ActiveField.NONE) {
            lastActiveSearchField = field
        }
        _uiState.update { it.copy(activeField = field) }
    }

    fun onFieldBlurred(field: ActiveField) {
        if (_uiState.value.activeField == field) {
            _uiState.update { it.copy(activeField = ActiveField.NONE) }
        }
    }

    fun onOriginQueryChanged(text: String) {
        lastActiveSearchField = ActiveField.ORIGIN
        _uiState.update {
            it.copy(
                activeField = ActiveField.ORIGIN,
                originQuery = text,
                origin = if (text.isBlank()) null else it.origin
            )
        }
        executeLocationSearch(text)
    }

    fun onDestinationQueryChanged(text: String) {
        lastActiveSearchField = ActiveField.DESTINATION
        _uiState.update {
            it.copy(
                activeField = ActiveField.DESTINATION,
                destinationQuery = text,
                destination = if (text.isBlank()) null else it.destination
            )
        }
        executeLocationSearch(text)
    }

    fun onQueryChanged(text: String) {
        val currentField = if (_uiState.value.activeField != ActiveField.NONE) {
            _uiState.value.activeField
        } else {
            lastActiveSearchField
        }
        if (currentField == ActiveField.ORIGIN) {
            onOriginQueryChanged(text)
        } else {
            onDestinationQueryChanged(text)
        }
    }

    private fun executeLocationSearch(text: String) {
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
        val targetField = if (lastActiveSearchField != ActiveField.NONE) {
            lastActiveSearchField
        } else if (_uiState.value.activeField != ActiveField.NONE) {
            _uiState.value.activeField
        } else if (_uiState.value.origin == null) {
            ActiveField.ORIGIN
        } else {
            ActiveField.DESTINATION
        }

        if (targetField == ActiveField.ORIGIN) {
            savedOriginText = loc.name
            _uiState.update {
                it.copy(
                    origin = target,
                    originQuery = loc.name,
                    searchResults = emptyList(),
                    activeField = if (it.destination == null) ActiveField.DESTINATION else ActiveField.NONE
                )
            }
            if (_uiState.value.destination == null) {
                lastActiveSearchField = ActiveField.DESTINATION
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
        startLocationUpdates()
        val userLoc = getBestLocation()
        val isFresh = isLocationFresh(userLoc)
        val latE6 = if (isFresh && userLoc != null) (userLoc.latitude * 1_000_000).toInt() else 0
        val lonE6 = if (isFresh && userLoc != null) (userLoc.longitude * 1_000_000).toInt() else 0
        val loc = TripPlanLocation(
            caption = "Minha localização",
            latE6 = latE6,
            lonE6 = lonE6,
            isUserLocation = true
        )
        val targetField = if (lastActiveSearchField != ActiveField.NONE) {
            lastActiveSearchField
        } else {
            ActiveField.ORIGIN
        }
        if (targetField == ActiveField.ORIGIN) {
            savedOriginText = "Minha localização"
            _uiState.update {
                it.copy(
                    origin = loc,
                    originQuery = "Minha localização",
                    searchResults = emptyList(),
                    activeField = if (it.destination == null) ActiveField.DESTINATION else ActiveField.NONE
                )
            }
            if (_uiState.value.destination == null) {
                lastActiveSearchField = ActiveField.DESTINATION
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
        if (isFresh) {
            triggerTripSearchIfReady()
        } else {
            scheduleStaleLocationFallback()
        }
    }

    fun startMapPicker(targetField: ActiveField) {
        val effectiveField = if (targetField != ActiveField.NONE) {
            targetField
        } else {
            lastActiveSearchField
        }
        _uiState.update {
            it.copy(
                isPickingOnMap = true,
                mapPickerTargetField = effectiveField,
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

    fun setTimeModeAndDateTime(mode: TripTimeMode, targetTimeMillis: Long) {
        val now = System.currentTimeMillis()
        val finalTime = if (mode == TripTimeMode.DEPART_NOW) {
            now
        } else {
            if (mode == TripTimeMode.DEPART_AT && targetTimeMillis < now) now else targetTimeMillis
        }
        _uiState.update {
            it.copy(
                timeMode = mode,
                targetTimeUtc = finalTime
            )
        }
        triggerTripSearchIfReady()
    }

    fun setTimeModeAndCustomTime(mode: TripTimeMode, hourOfDay: Int, minute: Int) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = _uiState.value.targetTimeUtc
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val newTime = cal.timeInMillis
        _uiState.update {
            it.copy(
                timeMode = mode,
                targetTimeUtc = newTime
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

    fun setPreferencesSheetOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isPreferencesSheetOpen = isOpen) }
    }

    fun setModalFilters(allowBus: Boolean, allowSubway: Boolean) {
        if (!allowBus && !allowSubway) return
        _uiState.update {
            it.copy(
                allowBus = allowBus,
                allowSubway = allowSubway
            )
        }
        triggerTripSearchIfReady()
    }

    fun setPreference(pref: TripPreference) {
        _uiState.update { it.copy(preference = pref) }
        triggerTripSearchIfReady()
    }

    fun resetPreferences() {
        _uiState.update {
            it.copy(
                preference = TripPreference.FASTEST,
                allowBus = true,
                allowSubway = true
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

    fun searchTrips(debounceMs: Long = 0L, isUserInitiated: Boolean = true) {
        val origin = _uiState.value.origin ?: return
        val destination = _uiState.value.destination ?: return

        liveDeparturesJob?.cancel()
        tripSearchJob?.cancel()
        tripSearchJob = viewModelScope.launch {
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            if (isUserInitiated) {
                _uiState.update {
                    it.copy(
                        isSearchingTrips = true,
                        itineraries = emptyList(),
                        tripSearchError = null,
                        hasSearched = true,
                        searchResults = emptyList()
                    )
                }
            } else {
                val hadItineraries = _uiState.value.itineraries.isNotEmpty()
                _uiState.update {
                    it.copy(
                        isSearchingTrips = !hadItineraries,
                        tripSearchError = null,
                        hasSearched = true,
                        searchResults = emptyList()
                    )
                }
            }
            try {
                val filter = TripPlanFilter(
                    mode = _uiState.value.timeMode,
                    targetTimeUtc = _uiState.value.targetTimeUtc,
                    preference = _uiState.value.preference,
                    allowBus = _uiState.value.allowBus,
                    allowSubway = _uiState.value.allowSubway
                )
                val results = repository.searchTripPlan(origin, destination, filter)
                val evaluatedResults = TripInsightsEvaluator.evaluate(results)
                val emptyMsg = when {
                    !_uiState.value.allowBus && _uiState.value.allowSubway -> "Nenhum trajeto encontrado usando apenas Metrô."
                    _uiState.value.allowBus && !_uiState.value.allowSubway -> "Nenhum trajeto encontrado usando apenas Ônibus."
                    else -> "Nenhum trajeto encontrado para este horário."
                }
                _uiState.update {
                    it.copy(
                        itineraries = evaluatedResults,
                        isSearchingTrips = false,
                        tripSearchError = if (evaluatedResults.isEmpty()) emptyMsg else null
                    )
                }
                if (evaluatedResults.isNotEmpty() && isSearchForNow()) {
                    startLiveDeparturesTicker()
                } else {
                    liveDeparturesJob?.cancel()
                }
            } catch (e: CancellationException) {
                // Cancelamento normal de corrotina (mudanca de horario ou novo filtro)
                throw e
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

    private fun isSearchForNow(): Boolean {
        val now = System.currentTimeMillis()
        val mode = _uiState.value.timeMode
        val target = _uiState.value.targetTimeUtc
        return mode == TripTimeMode.DEPART_NOW ||
                (mode == TripTimeMode.DEPART_AT && Math.abs(target - now) <= 30 * 60 * 1000L)
    }

    private fun startLiveDeparturesTicker() {
        liveDeparturesJob?.cancel()
        if (!isSearchForNow()) return
        liveDeparturesJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                if (!isSearchForNow()) break
                val currentItineraries = _uiState.value.itineraries
                if (currentItineraries.isNotEmpty()) {
                    try {
                        val updated = repository.enrichItinerariesWithDepartures(currentItineraries, isForNow = true)
                        val evaluated = TripInsightsEvaluator.evaluate(updated)
                        if (evaluated != currentItineraries) {
                            _uiState.update { it.copy(itineraries = evaluated) }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun triggerTripSearchIfReady(debounceMs: Long = 300L, isUserInitiated: Boolean = true) {
        val orig = _uiState.value.origin
        val dest = _uiState.value.destination
        if (orig != null && dest != null) {
            if (orig.isUserLocation && orig.latE6 == 0 && orig.lonE6 == 0) {
                _uiState.update { it.copy(isSearchingTrips = true) }
                return
            }
            if (dest.isUserLocation && dest.latE6 == 0 && dest.lonE6 == 0) {
                _uiState.update { it.copy(isSearchingTrips = true) }
                return
            }
            searchTrips(debounceMs, isUserInitiated)
        }
    }

    suspend fun reverseGeocode(latE6: Int, lonE6: Int): String {
        return repository.reverseGeocode(latE6, lonE6)
    }

    private fun handleFreshLocation(loc: Location) {
        val latE6 = (loc.latitude * 1_000_000).toInt()
        val lonE6 = (loc.longitude * 1_000_000).toInt()
        var updated = false

        val currentOrigin = _uiState.value.origin
        if (currentOrigin != null && currentOrigin.isUserLocation) {
            val isUnset = currentOrigin.latE6 == 0 && currentOrigin.lonE6 == 0
            val dist = FloatArray(1)
            Location.distanceBetween(
                currentOrigin.latitude, currentOrigin.longitude,
                loc.latitude, loc.longitude, dist
            )
            if (isUnset || dist[0] > 150f) {
                val newOrigin = currentOrigin.copy(latE6 = latE6, lonE6 = lonE6)
                _uiState.update { it.copy(origin = newOrigin) }
                updated = true
            }
        }

        val currentDest = _uiState.value.destination
        if (currentDest != null && currentDest.isUserLocation) {
            val isUnset = currentDest.latE6 == 0 && currentDest.lonE6 == 0
            val dist = FloatArray(1)
            Location.distanceBetween(
                currentDest.latitude, currentDest.longitude,
                loc.latitude, loc.longitude, dist
            )
            if (isUnset || dist[0] > 150f) {
                val newDest = currentDest.copy(latE6 = latE6, lonE6 = lonE6)
                _uiState.update { it.copy(destination = newDest) }
                updated = true
            }
        }

        if (updated) {
            fallbackJob?.cancel()
            triggerTripSearchIfReady(debounceMs = 300L, isUserInitiated = false)
        }
    }

    private fun scheduleStaleLocationFallback() {
        fallbackJob?.cancel()
        fallbackJob = viewModelScope.launch {
            delay(3500L)
            val orig = _uiState.value.origin
            val dest = _uiState.value.destination
            val needsOrig = orig != null && orig.isUserLocation && orig.latE6 == 0 && orig.lonE6 == 0
            val needsDest = dest != null && dest.isUserLocation && dest.latE6 == 0 && dest.lonE6 == 0
            if (needsOrig || needsDest) {
                val fallbackLoc = getBestLocation()
                if (fallbackLoc != null) {
                    val fbLat = (fallbackLoc.latitude * 1_000_000).toInt()
                    val fbLon = (fallbackLoc.longitude * 1_000_000).toInt()
                    _uiState.update { state ->
                        var newOrig = state.origin
                        var newDest = state.destination
                        if (needsOrig && newOrig != null) {
                            newOrig = newOrig.copy(latE6 = fbLat, lonE6 = fbLon)
                        }
                        if (needsDest && newDest != null) {
                            newDest = newDest.copy(latE6 = fbLat, lonE6 = fbLon)
                        }
                        state.copy(origin = newOrig, destination = newDest)
                    }
                    triggerTripSearchIfReady(debounceMs = 0L)
                }
            }
        }
    }

    private fun isLocationFresh(loc: Location?): Boolean {
        if (loc == null) return false
        val ageMs = System.currentTimeMillis() - loc.time
        return ageMs in 0..45_000L
    }

    private fun getBestLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return try {
            val gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val fused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try { lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER) } catch (_: Exception) { null }
            } else null

            listOfNotNull(gps, net, fused).maxByOrNull { it.time }
        } catch (_: SecurityException) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
        fallbackJob?.cancel()
        locationSearchJob?.cancel()
        tripSearchJob?.cancel()
        liveDeparturesJob?.cancel()
    }
}

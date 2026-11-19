package com.dfmob.data.model

import java.util.UUID

data class FavoriteTrip(
    val id: String = UUID.randomUUID().toString(),
    val customName: String,
    val origin: TripPlanLocation,
    val destination: TripPlanLocation
)

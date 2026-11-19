package com.dfmob.data.model

data class TransitLine(
    val routeId: String,
    val agencyId: String,
    val agencyName: String,
    val lineNumber: String,
    val routeName: String,
    val transitType: Int, // 3 = Onibus, 1 = Metro
    val colorHex: String,
    val textColorHex: String,
    val price: Double?,
    val directionId: Int? = null,
    val destinationName: String? = null,
    val isCircular: Boolean = false
) {
    val isBus: Boolean get() = transitType == 3
    val isMetro: Boolean get() = transitType == 1

    val isExecutive: Boolean
        get() = lineNumber == "0.108" ||
                routeName.contains("Executivo", ignoreCase = true) ||
                agencyName.contains("Executivo", ignoreCase = true)

    val hasTariffIntegration: Boolean
        get() = !isExecutive && (isBus || isMetro)

    val formattedPrice: String
        get() = if (price != null && price > 0.0) {
            String.format(java.util.Locale("pt", "BR"), "R$ %.2f", price)
        } else {
            "Tarifa GDF"
        }
}

enum class TransitModeFilter {
    BUS,
    METRO
}

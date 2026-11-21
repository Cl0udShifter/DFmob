package com.dfmob.data.model

enum class CongestionLevel(val value: Int) {
    EMPTY(0),
    FEW(1),
    LOW(2),
    MED(3),
    HIGH(4),
    FULL(5),
    PACKED(6);

    companion object {
        fun fromValue(v: Int): CongestionLevel {
            return entries.firstOrNull { it.value == v } ?: LOW
        }
    }
}

enum class CongestionSource(val value: Int) {
    SENSOR(1),
    USER(2),
    PREDICTION(3);

    companion object {
        fun fromValue(v: Int): CongestionSource {
            return entries.firstOrNull { it.value == v } ?: PREDICTION
        }
    }
}

enum class CrowdingCategory {
    LOW,
    MEDIUM,
    HIGH
}

data class CrowdingInfo(
    val level: CongestionLevel = CongestionLevel.LOW,
    val source: CongestionSource = CongestionSource.PREDICTION
) {
    val category: CrowdingCategory
        get() = when (level) {
            CongestionLevel.EMPTY, CongestionLevel.FEW, CongestionLevel.LOW -> CrowdingCategory.LOW
            CongestionLevel.MED, CongestionLevel.HIGH -> CrowdingCategory.MEDIUM
            CongestionLevel.FULL, CongestionLevel.PACKED -> CrowdingCategory.HIGH
        }

    val displayLabel: String
        get() = if (source == CongestionSource.PREDICTION) {
            when (category) {
                CrowdingCategory.LOW -> "Geralmente com assentos"
                CrowdingCategory.MEDIUM -> "Geralmente apenas em pé"
                CrowdingCategory.HIGH -> "Geralmente lotado"
            }
        } else {
            when (category) {
                CrowdingCategory.LOW -> "Assentos disponíveis"
                CrowdingCategory.MEDIUM -> "Apenas em pé"
                CrowdingCategory.HIGH -> "Lotado"
            }
        }

    val shortLabel: String
        get() = when (category) {
            CrowdingCategory.LOW -> "Assentos livres"
            CrowdingCategory.MEDIUM -> "Apenas em pé"
            CrowdingCategory.HIGH -> "Lotado"
        }

    companion object {
        val DEFAULT_LOW = CrowdingInfo(CongestionLevel.LOW, CongestionSource.PREDICTION)
        val DEFAULT_MEDIUM = CrowdingInfo(CongestionLevel.MED, CongestionSource.PREDICTION)
        val DEFAULT_HIGH = CrowdingInfo(CongestionLevel.FULL, CongestionSource.PREDICTION)
    }
}

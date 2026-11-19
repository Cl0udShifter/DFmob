package com.dfmob.data.model

data class SemobAlert(
    val id: String,
    val title: String,
    val description: String,
    val category: String, // "OBRAS", "DESVIO", "FERIADO", "OPERACAO"
    val dateFormatted: String,
    val affectedLineNumbers: List<String>,
    val timestampMs: Long = System.currentTimeMillis()
)

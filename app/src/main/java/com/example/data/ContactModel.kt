package com.example.data

data class ContactModel(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val isFavorite: Boolean = false,
    val lookupKey: String? = null
)

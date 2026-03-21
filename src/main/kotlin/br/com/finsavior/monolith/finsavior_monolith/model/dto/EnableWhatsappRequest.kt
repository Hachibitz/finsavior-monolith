package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.validation.PhoneNumber

data class EnableWhatsappRequest(
    val isEnabled: Boolean,
    
    @field:PhoneNumber
    val phoneNumber: String
)

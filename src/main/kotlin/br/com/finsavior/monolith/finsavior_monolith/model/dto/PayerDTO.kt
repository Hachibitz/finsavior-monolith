package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class PayerDTO (
    val name: NameDTO,
    val externalEmailAddress:String,
    val payerId: String,
    val address: AddressDTO
)
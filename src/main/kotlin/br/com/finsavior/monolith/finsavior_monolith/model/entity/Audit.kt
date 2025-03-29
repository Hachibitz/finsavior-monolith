package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.FlagEnum
import jakarta.persistence.Column
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.LocalDateTime

data class Audit(
    @Column(name = "del_fg")
    @Enumerated(EnumType.STRING)
    private var delFg: FlagEnum,

    @Column(name = "insert_dtm")
    private var insertDtm: LocalDateTime? = null,

    @Column(name = "insert_id")
    private var insertId: String? = null,

    @Column(name = "update_dtm")
    private var updateDtm: LocalDateTime? = null,

    @Column(name = "update_id")
    private var updateId: String? = null
)

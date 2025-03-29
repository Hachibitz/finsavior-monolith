package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.CommonEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.FlagEnum
import jakarta.persistence.Column
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.LocalDateTime

data class Audit(
    @Column(name = "del_fg")
    @Enumerated(EnumType.STRING)
    var delFg: FlagEnum = FlagEnum.N,

    @Column(name = "insert_dtm")
    var insertDtm: LocalDateTime? = LocalDateTime.now(),

    @Column(name = "insert_id")
    var insertId: String? = CommonEnum.APP_ID.value,

    @Column(name = "update_dtm")
    var updateDtm: LocalDateTime? = LocalDateTime.now(),

    @Column(name = "update_id")
    var updateId: String? = CommonEnum.APP_ID.value
)

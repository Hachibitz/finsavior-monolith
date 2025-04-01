package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "bill_table_data")
class BillTableData (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column(name = "user_id")
    val userId: Long,

    @Column(name = "bill_type")
    val billType: String,

    @Column(name = "bill_date")
    var billDate: String,

    @Column(name = "bill_name")
    var billName: String,

    @Column(name = "bill_value")
    var billValue: BigDecimal,

    @Column(name = "bill_description")
    var billDescription: String? = null,

    @Column(name = "bill_table")
    @Enumerated(EnumType.STRING)
    var billTable: BillTableEnum,

    @Column(name = "is_paid")
    var isPaid: Boolean,

    @Embedded
    var audit: Audit? = null
)
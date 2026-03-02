package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillEntryMethodEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "bill_table_data")
class BillTableData(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "bill_type")
    val billType: String,

    @Column(name = "bill_date", nullable = false)
    var billDate: String,

    @Column(name = "bill_name")
    var billName: String,

    @Column(name = "bill_value", nullable = false)
    var billValue: BigDecimal,

    @Column(name = "bill_description")
    var billDescription: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_table", nullable = false)
    var billTable: BillTableEnum,

    @Column(name = "bill_category")
    var billCategory: String?,

    @Column(name = "is_paid", nullable = false)
    var isPaid: Boolean = false,

    @Column(name = "is_installment")
    var isInstallment: Boolean? = false,

    @Column(name = "total_installments")
    var totalInstallments: Int? = null,

    @Column(name = "current_installment")
    var currentInstallment: Int? = null,

    @Column(name = "is_recurrent")
    var isRecurrent: Boolean? = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_method")
    var entryMethod: BillEntryMethodEnum = BillEntryMethodEnum.MANUAL,

    @Column(name = "payment_type")
    var paymentType: String? = null,

    @Column(name = "card_id")
    var cardId: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_id")
    var installment: Installment? = null,

    @Embedded
    var audit: Audit? = null
)

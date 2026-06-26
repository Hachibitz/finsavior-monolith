package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTypeEnum
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * Parent record of a recurring ("fixed") bill. Each calendar month a real
 * [BillTableData] row is materialized from this template (see FixedBillService),
 * mirroring the way [Installment] groups its child bills. Storing the template
 * here makes it possible to edit/delete a whole recurring bill in one place and
 * to keep generating it indefinitely via the monthly scheduler.
 */
@Entity
@Table(
    name = "fixed_bill",
    indexes = [
        Index(name = "idx_fixed_bill_user_active", columnList = "user_id,active")
    ]
)
class FixedBill(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "bill_name", length = 100)
    var billName: String,

    @Column(name = "bill_value", nullable = false)
    var billValue: BigDecimal,

    @Column(name = "bill_description", length = 255)
    var billDescription: String? = null,

    @Column(name = "bill_category", length = 60)
    var billCategory: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_type")
    var billType: BillTypeEnum,

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_table", nullable = false)
    var billTable: BillTableEnum,

    @Column(name = "payment_type", length = 30)
    var paymentType: String? = null,

    @Column(name = "card_id", length = 64)
    var cardId: String? = null,

    /** Day of month (1-31) the bill actually falls due, used to build the purchase date of each instance. */
    @Column(name = "day_of_month")
    var dayOfMonth: Int? = null,

    /** First billing month this fixed bill was created for, e.g. "Jun 2026". */
    @Column(name = "start_bill_date", length = 20)
    var startBillDate: String,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @OneToMany(mappedBy = "fixedBill", cascade = [CascadeType.ALL], orphanRemoval = true)
    val bills: MutableList<BillTableData> = mutableListOf(),

    @Embedded
    var audit: Audit? = null
)

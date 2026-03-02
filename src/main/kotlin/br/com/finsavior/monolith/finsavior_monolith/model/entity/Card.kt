package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.CardStyleEnum
import jakarta.persistence.*

@Entity
@Table(name = "card")
data class Card(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "name")
    var name: String,

    @Column(name = "style")
    @Enumerated(EnumType.STRING)
    var style: CardStyleEnum? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User?,

    @Embedded
    var audit: Audit? = null
) {
    constructor(): this(null, "", null, null, null)
}

package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.Installment
import org.springframework.data.jpa.repository.JpaRepository

interface InstallmentRepository : JpaRepository<Installment, Long>

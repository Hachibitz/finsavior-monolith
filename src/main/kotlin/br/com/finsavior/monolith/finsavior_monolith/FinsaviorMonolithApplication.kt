package br.com.finsavior.monolith.finsavior_monolith

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@EnableTransactionManagement
class FinsaviorMonolithApplication

fun main(args: Array<String>) {
	runApplication<FinsaviorMonolithApplication>(*args)
}

package br.com.finsavior.monolith.finsavior_monolith.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class PromptConfig {

    @Value("\${prompt-monthly-part1}")
    lateinit var promptMonthlyPart1: String

    @Value("\${prompt-monthly-part2}")
    lateinit var promptMonthlyPart2: String

    @Value("\${prompt-monthly-part3}")
    lateinit var promptMonthlyPart3: String

    @Value("\${prompt-monthly-part4}")
    lateinit var promptMonthlyPart4: String

    @Value("\${prompt-trimester-part1}")
    lateinit var promptTrimesterPart1: String

    @Value("\${prompt-trimester-part2}")
    lateinit var promptTrimesterPart2: String

    @Value("\${prompt-trimester-part3}")
    lateinit var promptTrimesterPart3: String

    @Value("\${prompt-trimester-part4}")
    lateinit var promptTrimesterPart4: String

    @Value("\${prompt-annual-part1}")
    lateinit var promptAnnualPart1: String

    @Value("\${prompt-annual-part2}")
    lateinit var promptAnnualPart2: String

    @Value("\${prompt-annual-part3}")
    lateinit var promptAnnualPart3: String

    @Value("\${prompt-annual-part4}")
    lateinit var promptAnnualPart4: String
}
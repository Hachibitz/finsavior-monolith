package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiAdviceDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiAdviceResponseDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiAnalysisDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.QuickInsightDTO
import br.com.finsavior.monolith.finsavior_monolith.service.AiAdviceService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("ai-advice")
class AiAdviceController(
    private val service: AiAdviceService
) {

    @PostMapping("/generate-ai-advice-and-insights")
    @ResponseStatus(HttpStatus.OK)
    fun generateAiAdviceAndInsights(@RequestBody aiAdvice: AiAdviceDTO): AiAdviceResponseDTO =
        service.generateAiAdviceWithAutoPrompt(aiAdvice)

    @GetMapping("/get-ai-advice-and-insights")
    @ResponseStatus(HttpStatus.OK)
    fun getAiAdviceAndInsights(): List<AiAnalysisDTO> =
        service.getAiAnalysisList()

    @GetMapping("/get-ai-advice/{adviceId}")
    @ResponseStatus(HttpStatus.OK)
    fun getAiAdviceAndInsights(@PathVariable adviceId: Long): AiAnalysisDTO =
        service.getAiAdviceById(adviceId)

    @DeleteMapping("/delete-analysis/{analysisId}")
    @ResponseStatus(HttpStatus.OK)
    fun deleteAnalysis(@PathVariable analysisId: Long) =
        service.deleteAnalysis(analysisId)

    @GetMapping("/validate-has-coverage/{analysisId}")
    @ResponseStatus(HttpStatus.OK)
    fun validateHasCoverage(@PathVariable analysisId: Int): Boolean =
        service.validateHasCoverage(analysisId)

    @GetMapping("/quick-insight")
    @ResponseStatus(HttpStatus.OK)
    fun getQuickInsight(@RequestParam(required = false) date: String?): QuickInsightDTO =
        service.getQuickInsight(date)
}
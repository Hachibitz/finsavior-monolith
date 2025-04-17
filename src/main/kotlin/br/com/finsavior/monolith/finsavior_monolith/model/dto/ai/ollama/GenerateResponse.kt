package br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.ollama

import com.fasterxml.jackson.annotation.JsonProperty

data class GenerateResponse(
    @JsonProperty("model")
    val model: String,

    @JsonProperty("created_at")
    val createdAt: String,

    @JsonProperty("response")
    val response: String,

    @JsonProperty("done")
    val done: Boolean,

    @JsonProperty("done_reason")
    val doneReason: String? = null,

    @JsonProperty("context")
    val context: List<Int>? = null,

    @JsonProperty("total_duration")
    val totalDuration: Long? = null,

    @JsonProperty("load_duration")
    val loadDuration: Long? = null,

    @JsonProperty("prompt_eval_count")
    val promptEvalCount: Int? = null,

    @JsonProperty("prompt_eval_duration")
    val promptEvalDuration: Long? = null,

    @JsonProperty("eval_count")
    val evalCount: Int? = null,

    @JsonProperty("eval_duration")
    val evalDuration: Long? = null
)

package br.com.finsavior.monolith.finsavior_monolith.model.enums

enum class CurrentFinancialSituationEnum(val description: String) {
    AMARELO("Atenção: Sua liquidez está positiva, mas baixa. Considere ajustes para garantir mais segurança financeira."),
    AZUL("Ótimo trabalho! Sua situação financeira está estável e saudável. Continue mantendo boas práticas financeiras."),
    VERMELHO("Você está no vermelho. Considere rever suas despesas e fazer uma análise detalhada para planejar melhor seus pagamentos.")
}
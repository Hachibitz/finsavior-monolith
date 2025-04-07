package br.com.finsavior.monolith.finsavior_monolith.config

import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Configuration

@Configuration
@EnableFeignClients(basePackages = ["br.com.finsavior.monolith.finsavior_monolith.integration.client"])
class FeignClientConfig
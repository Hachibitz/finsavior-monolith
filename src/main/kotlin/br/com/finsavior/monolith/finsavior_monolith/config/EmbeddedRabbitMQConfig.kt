package br.com.finsavior.monolith.finsavior_monolith.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.fridujo.rabbitmq.mock.MockConnectionFactory
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EmbeddedRabbitMQConfig {

    @Bean
    fun connectionFactory(): ConnectionFactory {
        return CachingConnectionFactory(MockConnectionFactory())
    }

    @Bean
    fun jsonMessageConverter(objectMapper: ObjectMapper): MessageConverter {
        return Jackson2JsonMessageConverter(objectMapper)
    }

    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        jsonMessageConverter: MessageConverter
    ): RabbitTemplate {
        return RabbitTemplate(connectionFactory).apply {
            messageConverter = jsonMessageConverter
        }
    }

    /*funcional
    @Bean
    fun rabbitTemplate(connectionFactory: ConnectionFactory): RabbitTemplate {
        return RabbitTemplate(connectionFactory)
    }*/

    @Bean
    fun rabbitAdmin(connectionFactory: ConnectionFactory): RabbitAdmin {
        return RabbitAdmin(connectionFactory).apply {
            // Cria as filas automaticamente ao iniciar
            afterPropertiesSet()
        }
    }

    @Bean
    fun deleteAccountQueue(): Queue {
        return Queue("delete.account.queue", true, false, false)
    }

    @Bean
    fun listenerContainerFactory(connectionFactory: ConnectionFactory): SimpleRabbitListenerContainerFactory {
        return SimpleRabbitListenerContainerFactory().apply {
            setConnectionFactory(connectionFactory)
            setMissingQueuesFatal(false)
        }
    }
}
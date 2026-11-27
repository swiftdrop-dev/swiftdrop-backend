package com.swiftdrop.dispatch.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMqConfig {

    public static final String EXCHANGE_NAME = "swiftdrop.exchange";
    public static final String PARCEL_CREATED_QUEUE = "dispatch.parcel-created.queue";
    public static final String ROUTING_KEY_PARCEL_CREATED = "parcel.created";

    public static final String DLX_NAME = "swiftdrop.dlx";
    public static final String PARCEL_CREATED_DLQ = "dispatch.parcel-created.dlq";
    public static final String PARCEL_CREATED_DLQ_ROUTING_KEY = "dispatch.parcel-created.dlq";

    @Bean
    public TopicExchange swiftDropExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue parcelCreatedQueue() {
        return QueueBuilder.durable(PARCEL_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", PARCEL_CREATED_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue parcelCreatedDlq() {
        return QueueBuilder.durable(PARCEL_CREATED_DLQ).build();
    }

    @Bean
    public Binding parcelCreatedBinding(Queue parcelCreatedQueue, TopicExchange swiftDropExchange) {
        return BindingBuilder.bind(parcelCreatedQueue)
                .to(swiftDropExchange)
                .with(ROUTING_KEY_PARCEL_CREATED);
    }

    @Bean
    public Binding parcelCreatedDlqBinding(Queue parcelCreatedDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(parcelCreatedDlq)
                .to(deadLetterExchange)
                .with(PARCEL_CREATED_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAutoStartup(autoStartup);
        factory.setDefaultRequeueRejected(false); // Dead-letter routing on fatal/rejected failures
        return factory;
    }
}

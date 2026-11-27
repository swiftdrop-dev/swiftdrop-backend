package com.swiftdrop.parcel.config;

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
    public static final String DLX_NAME = "swiftdrop.dlx";
    public static final String ROUTING_KEY_PARCEL_CREATED = "parcel.created";
    public static final String DISPATCH_EVENTS_QUEUE = "parcel.dispatch-events.queue";
    public static final String DISPATCH_EVENTS_DLQ = "parcel.dispatch-events.dlq";

    @Value("${spring.rabbitmq.listener.simple.auto-startup:true}")
    private boolean autoStartup;

    @Bean
    public TopicExchange swiftDropExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue dispatchEventsDeadLetterQueue() {
        return QueueBuilder.durable(DISPATCH_EVENTS_DLQ).build();
    }

    @Bean
    public Binding dispatchEventsDlqBinding() {
        return BindingBuilder.bind(dispatchEventsDeadLetterQueue())
                .to(deadLetterExchange())
                .with(DISPATCH_EVENTS_DLQ);
    }

    @Bean
    public Queue dispatchEventsQueue() {
        return QueueBuilder.durable(DISPATCH_EVENTS_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DISPATCH_EVENTS_DLQ)
                .build();
    }

    @Bean
    public Binding courierAcceptedBinding() {
        return BindingBuilder.bind(dispatchEventsQueue())
                .to(swiftDropExchange())
                .with("courier.accepted");
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
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}

package com.swiftdrop.tracking.config;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_NAME = "swiftdrop.exchange";
    public static final String DLX_NAME = "swiftdrop.dlx";
    public static final String TRACKING_QUEUE = "tracking.lifecycle.queue";
    public static final String TRACKING_DLQ = "tracking.lifecycle.dlq";

    @Bean
    public TopicExchange mainExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue trackingDeadLetterQueue() {
        return QueueBuilder.durable(TRACKING_DLQ).build();
    }

    @Bean
    public Binding trackingDlqBinding() {
        return BindingBuilder.bind(trackingDeadLetterQueue())
                .to(deadLetterExchange())
                .with(TRACKING_DLQ);
    }

    @Bean
    public Queue trackingQueue() {
        return QueueBuilder.durable(TRACKING_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", TRACKING_DLQ)
                .build();
    }

    @Bean
    public Binding parcelEventsBinding() {
        return BindingBuilder.bind(trackingQueue())
                .to(mainExchange())
                .with("parcel.#");
    }

    @Bean
    public Binding dispatchEventsBinding() {
        return BindingBuilder.bind(trackingQueue())
                .to(mainExchange())
                .with("dispatch.#");
    }

    @Bean
    public Binding courierEventsBinding() {
        return BindingBuilder.bind(trackingQueue())
                .to(mainExchange())
                .with("courier.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @org.springframework.beans.factory.annotation.Value("${spring.rabbitmq.listener.simple.auto-startup:true}")
    private boolean autoStartup;

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setDefaultRequeueRejected(false);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}

/*************************************************************************
 * Copyright (C) 2016-2019 PDX Technologies, Inc. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *************************************************************************/

package biz.pdxtech.iaas.amqp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange iaasExch() {
        return new TopicExchange("pdx.iaas.exch");
    }

    @Bean
    public Queue iaasQueue() {
        return new Queue("iaas." + UUID.randomUUID().toString(), false, true, true);
    }

    @Bean
    Binding iaasBinding(TopicExchange exchange) {
        return BindingBuilder.bind(iaasQueue()).to(exchange).with("pdx.iaas.iaas.#");
    }

    @Bean
    public Queue chainQueue() {
        return new Queue("chain." + UUID.randomUUID().toString(), false, true, true);
    }

    @Bean
    Binding chainBinding(TopicExchange exchange) {
        return BindingBuilder.bind(chainQueue()).to(exchange).with("pdx.iaas.chain.#");
    }

    @Bean
    public Queue nodeQueue() {
        return new Queue("node." + UUID.randomUUID().toString(), false, true, true);
    }

    @Bean
    Binding nodeBinding(TopicExchange exchange) {
        return BindingBuilder.bind(nodeQueue()).to(exchange).with("pdx.iaas.node.#");
    }

    @Bean
    public Queue dappQueue() {
        return new Queue("dapp." + UUID.randomUUID().toString(), false, true, true);
    }

    @Bean
    Binding dappBinding(TopicExchange exchange) {
        return BindingBuilder.bind(dappQueue()).to(exchange).with("pdx.iaas.dapp.#");
    }

    @Bean
    public Queue userQueue() {
        return new Queue("user." + UUID.randomUUID().toString(), false, true, true);
    }

    @Bean
    Binding userBinding(TopicExchange exchange) {
        return BindingBuilder.bind(userQueue()).to(exchange).with("pdx.iaas.user.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter jsonMessageConverter = new Jackson2JsonMessageConverter();
        jsonMessageConverter.setClassMapper(classMapper());
        return jsonMessageConverter;
    }

    @Bean
    public DefaultClassMapper classMapper() {
        DefaultClassMapper classMapper = new DefaultClassMapper();
        Map<String, Class<?>> idClassMapping = new HashMap<>();
        //ADD queue messages of interest to process
        idClassMapping.put("biz.pdxtech.iaas.amqp.QueMsg", QueMessage.class);
        classMapper.setIdClassMapping(idClassMapping);
        return classMapper;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory containerFactory(ConnectionFactory connectionFactory,
                                                                 SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }

    @Bean
    SimpleMessageListenerContainer container(
            ConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {
        SimpleMessageListenerContainer container
                = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setMessageListener(listenerAdapter);

        //ADD queues of interest to receive msg from.
        container.setQueueNames(iaasQueue().getName(), chainQueue().getName(),
                nodeQueue().getName(), userQueue().getName(), dappQueue().getName());

        return container;
    }

    @Bean
    MessageListenerAdapter listenerAdapter(Receiver receiver) {
        MessageListenerAdapter adaptor = new MessageListenerAdapter(receiver, jsonMessageConverter());
        return adaptor;
    }
}

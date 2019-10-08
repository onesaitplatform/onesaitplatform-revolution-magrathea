/**
 * Copyright Indra Soluciones Tecnologías de la Información, S.L.U.
 * 2013-2019 SPAIN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.minsait.onesait.platform.iotbroker.plugable.impl.gateway.reference.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minsait.onesait.platform.iotbroker.plugable.interfaces.gateway.GatewayInfo;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;

import java.util.Arrays;
import java.util.List;

@ConditionalOnProperty(prefix = "onesaitplatform.iotbroker.plugable.gateway.amqp", name = "enable", havingValue = "true")
@Configuration
@EnableRabbit
public class RabbitMqConfig implements RabbitListenerConfigurer {

  public static final String AMQP_GATEWAY = "amqp_gateway";
  public static final String MESSAGE_QUEUE = "message";
  public static final String TOPIC_MESSAGE_QUEUE = "topic/message";
  public static final String TOPIC_SUBSCRIPTION_QUEUE = "topic/subscription";
  public static final String TOPIC_COMMAND_QUEUE = "topic/command";

  @Bean
  Queue topicCommandQueue() {
    return new Queue(TOPIC_COMMAND_QUEUE, true);
  }

  @Bean
  Queue topicSubscriptionQueue() {
    return new Queue(TOPIC_SUBSCRIPTION_QUEUE, true);
  }

  @Bean
  Queue topicMessageQueue() {
    return new Queue(TOPIC_MESSAGE_QUEUE, true);
  }

  @Bean
  Queue messageQueue() {
    return new Queue(MESSAGE_QUEUE, true);
  }

  @Bean
  TopicExchange iotBrokerExchange() {
    return new TopicExchange("iotbroker");
  }

  @Bean
  List<Binding> binding() {

    return Arrays.asList(BindingBuilder.bind(messageQueue()).to(iotBrokerExchange()).with(MESSAGE_QUEUE),
                         BindingBuilder.bind(topicMessageQueue()).to(iotBrokerExchange()).with(TOPIC_MESSAGE_QUEUE),
                         BindingBuilder.bind(topicSubscriptionQueue()).to(iotBrokerExchange()).with(TOPIC_SUBSCRIPTION_QUEUE),
                         BindingBuilder.bind(topicCommandQueue()).to(iotBrokerExchange()).with(TOPIC_COMMAND_QUEUE));
  }

  @Override
  public void configureRabbitListeners(final RabbitListenerEndpointRegistrar registrar) {
    registrar.setMessageHandlerMethodFactory(messageHandlerMethodFactory());
  }

  @Bean
  public DefaultMessageHandlerMethodFactory messageHandlerMethodFactory() {
    DefaultMessageHandlerMethodFactory factory = new DefaultMessageHandlerMethodFactory();
    factory.setMessageConverter(consumerJackson2MessageConverter());
    return factory;
  }

  @Bean
  public MappingJackson2MessageConverter consumerJackson2MessageConverter() {
    return new MappingJackson2MessageConverter();
  }

  @Bean
  public RabbitTemplate rabbitTemplate(final ConnectionFactory connectionFactory) {
    final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(messageConverter());
    return rabbitTemplate;
  }

  @Bean
  public MessageConverter messageConverter() {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    return new Jackson2JsonMessageConverter(mapper);
  }

  public static GatewayInfo getGatewayInfo() {
    final GatewayInfo info = new GatewayInfo();
    info.setName(AMQP_GATEWAY);
    info.setProtocol("AMQP");

    return info;
  }
}

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

import com.minsait.onesait.platform.comms.protocol.SSAPMessage;
import com.minsait.onesait.platform.comms.protocol.body.SSAPBodyReturnMessage;
import com.minsait.onesait.platform.iotbroker.processor.GatewayNotifier;
import com.minsait.onesait.platform.iotbroker.processor.MessageProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Slf4j
public class RabbitMqHandler {

  private final GatewayNotifier subscription;
  private final MessageProcessor processor;
  private RabbitTemplate rabbitTemplate;

  @Autowired
  public RabbitMqHandler(GatewayNotifier subscription,
                         MessageProcessor processor,
                         RabbitTemplate rabbitTemplate){

    this.subscription = subscription;
    this.processor = processor;
    this.rabbitTemplate = rabbitTemplate;
  }


  @PostConstruct
  public void init() {
    subscription.addSubscriptionListener(RabbitMqConfig.AMQP_GATEWAY, s ->
      rabbitTemplate.convertAndSend(RabbitMqConfig.TOPIC_SUBSCRIPTION_QUEUE, s)
    );

    subscription.addCommandListener(RabbitMqConfig.AMQP_GATEWAY, c -> {
      rabbitTemplate.convertAndSend(RabbitMqConfig.TOPIC_COMMAND_QUEUE, c);
      return new SSAPMessage<>();
    });
  }

  @RabbitListener(queues = RabbitMqConfig.MESSAGE_QUEUE)
  public void handleMessage(SSAPMessage message){
    final SSAPMessage<SSAPBodyReturnMessage> response = processor.process(message, RabbitMqConfig.getGatewayInfo());

    rabbitTemplate.convertAndSend(RabbitMqConfig.TOPIC_MESSAGE_QUEUE, response);
  }

}

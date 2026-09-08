package com.aydindemir.health.notification.infrastructure.messaging;

import com.aydindemir.health.notification.application.port.in.DeliverNotificationUseCase;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RabbitNotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitNotificationListener.class);

    private final DeliverNotificationUseCase deliverNotification;

    public RabbitNotificationListener(DeliverNotificationUseCase deliverNotification) {
        this.deliverNotification = deliverNotification;
    }

    @RabbitListener(
            queues = NotificationRabbitContract.DELIVERY_QUEUE,
            autoStartup = "${app.messaging.rabbit-listener.enabled:false}")
    public void receive(
            @Payload NotificationTaskMessage task,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            deliverNotification.deliver(task.toCommand());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Notification task rejected: taskId={}, taskVersion={}, notificationType={}, failureType={}",
                    task.taskId(),
                    task.taskVersion(),
                    task.notificationType(),
                    exception.getClass().getSimpleName());
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }
}

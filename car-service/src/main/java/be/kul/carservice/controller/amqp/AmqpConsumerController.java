package be.kul.carservice.controller.amqp;

import be.kul.carservice.service.CarService;
import be.kul.carservice.utils.amqp.RabbitMQConfig;
import be.kul.carservice.utils.json.jsonObjects.amqpMessages.car.CarStateUpdate;
import be.kul.carservice.utils.json.jsonObjects.amqpMessages.payment.PaymentMethodStatusUpdate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

public class AmqpConsumerController {
    @Autowired
    CarService carService;

    @Autowired
    private ObjectMapper objectMapper;


    @RabbitListener(queues = RabbitMQConfig.CAR_STATE_QUEUE, containerFactory = "externalRabbitListenerContainerFactory")
    public void updateCarState(@Payload String stateUpdateString, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) throws JsonProcessingException {
        //get the stateUpdate object from the string
        CarStateUpdate stateUpdate = objectMapper.readValue(stateUpdateString, CarStateUpdate.class);

        //Get the car id from the routing key
        //Ex routing key: carService.cars.state.{carId}
        String carIdString = routingKey.split("\\.")[3];
        long carId = Integer.parseInt(carIdString);

        //Set the carId for the car state update
        stateUpdate.setCarId(carId);

        //Update the car state
        carService.updateCarState(stateUpdate);
    }

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_METHOD_STATUS_QUEUE, containerFactory = "internalRabbitListenerContainerFactory")
    public void updatePaymentMethodStatus(@Payload String paymentMethodStatusUpdateString) throws JsonProcessingException {
        //get the PaymentMethodStatusUpdate object from the string
        PaymentMethodStatusUpdate paymentMethodStatusUpdate = objectMapper.readValue(paymentMethodStatusUpdateString, PaymentMethodStatusUpdate.class);

        //Handle the new waypoint
        carService.updatePaymentMethodStatus(paymentMethodStatusUpdate);
    }
}

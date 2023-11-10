package com.microserviceDemo.orderservice.service;

import com.microserviceDemo.orderservice.dto.InventoryResponse;
import com.microserviceDemo.orderservice.dto.OrderLineItemsDto;
import com.microserviceDemo.orderservice.dto.OrderRequest;
import com.microserviceDemo.orderservice.event.OrderPlacedEvent;
import com.microserviceDemo.orderservice.model.Order;
import com.microserviceDemo.orderservice.model.OrderLineItems;
import com.microserviceDemo.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final Tracer tracer;

    private final KafkaTemplate<String,OrderPlacedEvent> kafkaTemplate;
    public String placeOrder(OrderRequest orderRequest){
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItemsList =  orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();
        order.setOrderLineItemList(orderLineItemsList);
//        orderLineItemsList.forEach(orderLineItems -> System.out.println(orderLineItems.getSkuCode()+orderLineItems.getQuantity()+orderLineItems.getPrice().toString()));
        List<String> skuCodes =  order.getOrderLineItemList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();
//        System.out.println("SkuCodes: "+ skuCodes);

        Span inventoryServiceLookup = tracer.nextSpan().name("InventoryServiceLookup");
        try(Tracer.SpanInScope spanInScope = tracer.withSpan(inventoryServiceLookup.start())){
            //        Call Inventory service and place order if product is in stock
            InventoryResponse[] inventoryResponseArray =  webClientBuilder.build().get()
                    .uri("http://inventory-service/api/inventory",
                            uriBuilder -> uriBuilder.queryParam("skuCode",skuCodes).build())
                    .retrieve()
                    .bodyToMono(InventoryResponse[].class)
                    .block();
//        System.out.println("InventoryResponse Array: " + Arrays.toString(inventoryResponseArray));
            boolean allProductsInStock = Arrays.stream(inventoryResponseArray).allMatch(InventoryResponse::isInStock);

            if (allProductsInStock){
                orderRepository.save(order);
                kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));
                return "Order Placed Successfully";
            }
            else{
                throw new IllegalArgumentException("Product is not in stock, please try again later");
            }
        } finally {
            inventoryServiceLookup.end();
        }


    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto){
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}

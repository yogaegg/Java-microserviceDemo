package com.microserviceDemo.orderservice.repository;

import com.microserviceDemo.orderservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}

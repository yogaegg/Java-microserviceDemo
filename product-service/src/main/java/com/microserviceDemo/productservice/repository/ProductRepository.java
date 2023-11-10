package com.microserviceDemo.productservice.repository;

import com.microserviceDemo.productservice.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product,String> {
}

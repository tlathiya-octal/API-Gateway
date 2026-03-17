package com.ecommerce.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
public class TestController {

    @GetMapping("/test")
    @Operation(summary = "Test")
    @ApiResponse(responseCode = "201", description = "Test API for the API Gateway")
    public ResponseEntity<String>test(){
        return ResponseEntity.ok("hello world");
    }
}

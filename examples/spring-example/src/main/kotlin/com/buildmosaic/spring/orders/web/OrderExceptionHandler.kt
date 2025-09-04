package com.buildmosaic.spring.orders.web

import com.buildmosaic.library.exception.OrderNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class OrderExceptionHandler {
  @ExceptionHandler(OrderNotFoundException::class)
  fun handle(ex: OrderNotFoundException): ResponseEntity<String> =
    ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.message)
}

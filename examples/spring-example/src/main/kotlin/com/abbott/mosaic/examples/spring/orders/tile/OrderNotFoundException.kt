package com.abbott.mosaic.examples.spring.orders.tile

class OrderNotFoundException(orderId: String, cause: Throwable? = null) :
  RuntimeException("Order $orderId not found", cause)

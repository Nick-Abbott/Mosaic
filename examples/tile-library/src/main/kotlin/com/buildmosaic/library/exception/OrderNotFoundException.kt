package com.buildmosaic.library.exception

class OrderNotFoundException(orderId: String, cause: Throwable? = null) :
  RuntimeException("Order $orderId not found", cause)

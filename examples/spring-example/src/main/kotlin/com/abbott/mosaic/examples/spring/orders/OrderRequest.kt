package com.abbott.mosaic.examples.spring.orders

import com.abbott.mosaic.MosaicRequest

/** Request object carrying the order identifier */
data class OrderRequest(val orderId: String) : MosaicRequest

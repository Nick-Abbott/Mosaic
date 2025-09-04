package com.buildmosaic.library

import com.buildmosaic.core.MosaicRequest

/** Request object carrying the order identifier */
data class OrderRequest(val orderId: String) : MosaicRequest

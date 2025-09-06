package org.buildmosaic.library

import org.buildmosaic.core.MosaicRequest

/** Request object carrying the order identifier */
data class OrderRequest(val orderId: String) : MosaicRequest

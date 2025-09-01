package com.abbott.mosaic.examples.ktor.orders

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.MosaicRegistry
import com.abbott.mosaic.examples.library.OrderRequest
import com.abbott.mosaic.examples.library.exception.OrderNotFoundException
import com.abbott.mosaic.examples.library.tile.OrderPageTile
import com.abbott.mosaic.examples.library.tile.OrderTotalTile
import com.abbott.mosaic.generated.registerGeneratedTiles
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    
    install(StatusPages) {
        exception<OrderNotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to cause.message))
        }
    }
    
    val registry = MosaicRegistry()
    registry.registerGeneratedTiles()
    
    routing {
        get("/orders/{id}") {
            val orderId = call.parameters["id"] ?: error("Missing order ID")
            val mosaic = Mosaic(registry, OrderRequest(orderId))
            val orderPage = mosaic.getTile<OrderPageTile>().get()
            call.respond(orderPage)
        }
        
        get("/orders/{id}/total") {
            val orderId = call.parameters["id"] ?: error("Missing order ID")
            val mosaic = Mosaic(registry, OrderRequest(orderId))
            val total = mosaic.getTile<OrderTotalTile>().get()
            call.respond(mapOf("total" to total))
        }
    }
}

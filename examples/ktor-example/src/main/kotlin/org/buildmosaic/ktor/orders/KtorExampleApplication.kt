package org.buildmosaic.ktor.orders

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MosaicRegistry
import org.buildmosaic.core.generated.registerGeneratedTiles
import org.buildmosaic.library.OrderRequest
import org.buildmosaic.library.exception.OrderNotFoundException
import org.buildmosaic.library.tile.OrderPageTile
import org.buildmosaic.library.tile.OrderTotalTile

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

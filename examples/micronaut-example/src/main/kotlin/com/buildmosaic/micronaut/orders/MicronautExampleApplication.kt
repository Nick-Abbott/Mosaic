package com.buildmosaic.micronaut.orders

import com.buildmosaic.core.Mosaic
import com.buildmosaic.core.MosaicRegistry
import com.buildmosaic.core.generated.registerGeneratedTiles
import com.buildmosaic.library.OrderRequest
import com.buildmosaic.library.exception.OrderNotFoundException
import com.buildmosaic.library.model.OrderPage
import com.buildmosaic.library.tile.OrderPageTile
import com.buildmosaic.library.tile.OrderTotalTile
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.runtime.Micronaut
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
  Micronaut.run(MicronautExampleApplication::class.java, *args)
}

class MicronautExampleApplication

@Factory
class MosaicConfiguration {
  @Bean
  @Singleton
  fun mosaicRegistry(): MosaicRegistry {
    val registry = MosaicRegistry()
    registry.registerGeneratedTiles()
    return registry
  }
}

@Controller("/orders")
class OrderController(private val registry: MosaicRegistry) {
  @Get("/{id}")
  fun getOrder(
    @PathVariable id: String,
  ): OrderPage =
    runBlocking {
      System.out.println(id)
      try {
        val mosaic = Mosaic(registry, OrderRequest(id))
        mosaic.getTile<OrderPageTile>().get()
      } catch (err: Exception) {
        System.out.println(err.toString())
        throw err
      }
    }

  @Get("/{id}/total")
  fun getOrderTotal(
    @PathVariable id: String,
  ): Map<String, Double> =
    runBlocking {
      val mosaic = Mosaic(registry, OrderRequest(id))
      val total = mosaic.getTile<OrderTotalTile>().get()
      mapOf("total" to total)
    }

  @Error(exception = OrderNotFoundException::class)
  fun handleOrderNotFound(exception: OrderNotFoundException): HttpResponse<Map<String, String>> {
    System.out.println(exception.message)
    return HttpResponse.status<Map<String, String>>(HttpStatus.NOT_FOUND)
      .body(mapOf("error" to (exception.message ?: "Order not found")))
  }
}

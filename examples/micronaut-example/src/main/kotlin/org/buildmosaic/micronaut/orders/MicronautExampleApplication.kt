package org.buildmosaic.micronaut.orders

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
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import org.buildmosaic.library.OrderKey
import org.buildmosaic.library.exception.OrderNotFoundException
import org.buildmosaic.library.model.OrderPage
import org.buildmosaic.library.tile.OrderPageTile
import org.buildmosaic.library.tile.OrderTotalTile

fun main(args: Array<String>) {
  Micronaut.run(MicronautExampleApplication::class.java, *args)
}

class MicronautExampleApplication

@Factory
class MosaicConfiguration {
  @Bean
  @Singleton
  fun mosaicCanvas(): Canvas {
    return kotlinx.coroutines.runBlocking {
      canvas { }
    }
  }
}

@Controller("/orders")
class OrderController(private val canvas: Canvas) {
  @Get("/{id}")
  fun getOrder(
    @PathVariable id: String,
  ): OrderPage =
    runBlocking {
      System.out.println(id)
      try {
        val mosaic =
          canvas.withLayer {
            single(OrderKey.qualifier) { id }
          }.create()
        mosaic.compose(OrderPageTile)
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
      val mosaic =
        canvas.withLayer {
          single(OrderKey.qualifier) { id }
        }.create()
      val total = mosaic.compose(OrderTotalTile)
      mapOf("total" to total)
    }

  @Error(exception = OrderNotFoundException::class)
  fun handleOrderNotFound(exception: OrderNotFoundException): HttpResponse<Map<String, String>> {
    System.out.println(exception.message)
    return HttpResponse.status<Map<String, String>>(HttpStatus.NOT_FOUND)
      .body(mapOf("error" to (exception.message ?: "Order not found")))
  }
}

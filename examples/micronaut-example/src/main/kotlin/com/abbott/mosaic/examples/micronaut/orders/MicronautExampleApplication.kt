package com.abbott.mosaic.examples.micronaut.orders

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.MosaicRegistry
import com.abbott.mosaic.examples.library.OrderRequest
import com.abbott.mosaic.examples.library.exception.OrderNotFoundException
import com.abbott.mosaic.examples.library.model.OrderPage
import com.abbott.mosaic.examples.library.tile.OrderPageTile
import com.abbott.mosaic.examples.library.tile.OrderTotalTile
import com.abbott.mosaic.generated.registerGeneratedTiles
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.runtime.Micronaut
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

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
    fun getOrder(@PathVariable id: String): OrderPage = runBlocking {
        val mosaic = Mosaic(registry, OrderRequest(id))
        mosaic.getTile<OrderPageTile>().get()
    }
    
    @Get("/{id}/total")
    fun getOrderTotal(@PathVariable id: String): Map<String, Double> = runBlocking {
        val mosaic = Mosaic(registry, OrderRequest(id))
        val total = mosaic.getTile<OrderTotalTile>().get()
        mapOf("total" to total)
    }
    
    @Error(OrderNotFoundException::class)
    fun handleOrderNotFound(exception: OrderNotFoundException): HttpResponse<Map<String, String>> {
        return HttpResponse.status<Map<String, String>>(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to (exception.message ?: "Order not found")))
    }
}

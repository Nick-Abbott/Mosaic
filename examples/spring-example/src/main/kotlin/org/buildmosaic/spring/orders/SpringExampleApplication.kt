package org.buildmosaic.spring.orders

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringExampleApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
  runApplication<SpringExampleApplication>(*args)
}

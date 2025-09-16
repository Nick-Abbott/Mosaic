package org.buildmosaic.spring.orders

import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.canvas
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MosaicConfig {
  @Bean
  suspend fun mosaicCanvas(): Canvas {
    return canvas { }
  }
}

package org.buildmosaic.spring.orders

import org.buildmosaic.core.vtwo.injection.Canvas
import org.buildmosaic.core.vtwo.injection.canvas
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MosaicConfig {
  @Bean
  suspend fun mosaicCanvas(): Canvas {
    return canvas { }
  }
}

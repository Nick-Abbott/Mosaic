package com.abbott.mosaic.examples.spring.orders

import com.abbott.mosaic.MosaicRegistry
import com.abbott.mosaic.generated.registerGeneratedTiles
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MosaicConfig {
  @Bean
  fun mosaicRegistry(): MosaicRegistry {
    val registry = MosaicRegistry()
    registry.registerGeneratedTiles()
    return registry
  }
}

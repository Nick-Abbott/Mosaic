package org.buildmosaic.spring.orders

import org.buildmosaic.core.MosaicRegistry
import org.buildmosaic.core.generated.registerGeneratedTiles
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

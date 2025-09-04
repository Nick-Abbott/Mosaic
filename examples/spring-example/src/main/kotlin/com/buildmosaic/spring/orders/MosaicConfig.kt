package com.buildmosaic.spring.orders

import com.buildmosaic.core.MosaicRegistry
import com.buildmosaic.core.generated.registerGeneratedTiles
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

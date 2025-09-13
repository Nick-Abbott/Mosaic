package org.buildmosaic.core.vtwo.exception

import kotlin.reflect.KClass

class MosaicMissingTypeException(val type: KClass<*>) :
  IllegalArgumentException("Type ${type.simpleName} is not available on the Canvas")

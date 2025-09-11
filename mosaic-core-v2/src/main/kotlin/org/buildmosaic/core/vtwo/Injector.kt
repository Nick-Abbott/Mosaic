package org.buildmosaic.core.vtwo

import kotlin.reflect.KClass

interface Injector {
  fun <T : Any> get(type: KClass<T>): T
}

inline fun <reified T : Any> Injector.get(): T = get(T::class)

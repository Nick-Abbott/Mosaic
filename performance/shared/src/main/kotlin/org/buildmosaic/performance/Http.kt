package org.buildmosaic.performance

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.scenarioRoutes(executor: ScenarioExecutor) {
  install(ContentNegotiation) { json() }
  routing {
    get("/health") { call.respond(HttpStatusCode.OK, mapOf("status" to "ok")) }
    post("/light") { call.respond(executor.light(call.receive<LightInput>())) }
    post("/aggregate") { call.respond(executor.aggregate(call.receive<AggregateInput>())) }
    post("/batching") { call.respond(executor.batching(call.receive<BatchingInput>())) }
    post("/coalescing") { call.respond(executor.coalescing(call.receive<BatchingInput>())) }
    post("/compute") { call.respond(executor.compute(call.receive<ComputeInput>())) }
  }
}

fun serve(config: AppConfig, executor: ScenarioExecutor) {
  embeddedServer(Netty, host = "0.0.0.0", port = config.port) {
    scenarioRoutes(executor)
  }.start(wait = true)
}

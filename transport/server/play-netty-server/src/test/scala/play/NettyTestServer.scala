/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play

import play.api.mvc._
import play.api.routing.sird._
import play.core.server._

object NettyTestServer extends App {
  lazy val Action = new ActionBuilder.IgnoringBody()(using _root_.controllers.Execution.trampoline)

  val port: Int = 8000

  private val serverConfig = ServerConfig(port = Some(port), address = "127.0.0.1")

  val server = NettyServer.fromRouterWithComponents(serverConfig) { c =>
    {
      case GET(p"/") =>
        c.defaultActionBuilder { implicit req => Results.Ok(s"Hello world") }
    }
  }
  println("Server (Netty) started: http://127.0.0.1:8000/ ")
  // server.stop()
}

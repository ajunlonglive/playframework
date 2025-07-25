/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.mvc._
import play.api.mvc.Results._
import play.api.test._
import play.core.server.Server
import play.libs.ws.ahc.AhcWSClient
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient

class WSSpec extends PlaySpecification with WsTestClient {
  sequential

  "WSClient.url().post(InputStream)" should {
    "uploads the stream" in {
      Server.withRouterFromComponents() { components =>
        import components.{ defaultActionBuilder => Action }
        import play.api.routing.sird.{ POST => SirdPost }
        import play.api.routing.sird._
        {
          case SirdPost(p"/") =>
            Action { (req: Request[AnyContent]) =>
              req.body.asRaw.fold[Result](BadRequest) { raw =>
                val size = raw.size
                Ok(s"size=$size")
              }
            }
        }
      } { implicit port =>
        withClient { ws =>
          val mat    = Materializer.matFromSystem(using ActorSystem())
          val javaWs = new AhcWSClient(ws.underlying[AsyncHttpClient], mat)
          val input  = this.getClass.getClassLoader.getResourceAsStream("play/libs/ws/play_full_color.png")
          val rep    = javaWs.url(s"http://localhost:$port/").post(input).toCompletableFuture.get()

          rep.getStatus must ===(200)
          rep.getBody must ===("size=20039")
        }
      }
    }
  }
}

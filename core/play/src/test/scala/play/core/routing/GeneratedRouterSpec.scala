/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.core.routing

import org.specs2.mutable.Specification
import play.api.http.DefaultHttpErrorHandler
import play.api.http.HttpErrorHandler
import play.api.mvc._
import play.api.routing.HandlerDef
import play.api.routing.Router
import play.core.j.JavaHandler
import play.core.test.FakeRequest

object GeneratedRouterSpec extends Specification {
  class TestRouter[H](
      handlerThunk: => H,
      handlerDef: HandlerDef,
      override val errorHandler: HttpErrorHandler = DefaultHttpErrorHandler,
      val prefix: String = "/"
  )(implicit hif: HandlerInvokerFactory[H])
      extends GeneratedRouter {
    override def withPrefix(prefix: String): Router =
      new TestRouter[H](handlerThunk, handlerDef, errorHandler, Router.concatPrefix(prefix, this.prefix))

    // The following code is based on the code generated by the routes compiler.

    private[this] lazy val route   = Route("GET", PathPattern(List(StaticPart(this.prefix))))
    private[this] lazy val invoker = createInvoker(
      handlerThunk,
      handlerDef
    )
    override def routes: PartialFunction[RequestHeader, Handler] = {
      case route(params) => call { invoker.call(handlerThunk) }
    }
    override def documentation: Seq[(String, String, String)] = List(
      ("GET", this.prefix, "TestRouter.handler")
    )
  }

  class JavaController extends play.mvc.Controller {
    def index = play.mvc.Results.ok("Hello world")
  }

  def routeToHandler[H, A](handlerThunk: => H, handlerDef: HandlerDef, request: RequestHeader)(
      block: Handler => A
  )(implicit hif: HandlerInvokerFactory[H]): A = {
    val router        = new TestRouter(handlerThunk, handlerDef)
    val request       = FakeRequest()
    val routedHandler = router.routes(request)
    block(routedHandler)
  }

  "A GeneratedRouter" should {
    "route requests to Scala controllers" in {
      val Action     = ActionBuilder.ignoringBody
      val handler    = Action(Results.Ok("Hello world"))
      val handlerDef = HandlerDef(
        handler.getClass.getClassLoader,
        "router",
        "ControllerClassName",
        "handler",
        Nil,
        "GET",
        "/",
        "Comment",
        Seq("Tag")
      )
      val request = FakeRequest()
      routeToHandler(handler, handlerDef, request) { (routedHandler: Handler) =>
        routedHandler must haveInterface[Handler.Stage]
        val (preprocessedRequest, preprocessedHandler) = Handler.applyStages(request, routedHandler)
        preprocessedHandler must_== handler
        preprocessedRequest.path must_== "/"
        preprocessedRequest.attrs(play.api.routing.Router.Attrs.HandlerDef) must_== handlerDef
      }
    }

    "route requests to Java controllers" in {
      val controller = new JavaController
      val handlerDef = HandlerDef(
        controller.getClass.getClassLoader,
        "router",
        controller.getClass.getName,
        "index",
        Nil,
        "GET",
        "/",
        "Comment",
        Seq("Tag")
      )
      val request = FakeRequest()
      routeToHandler(controller.index, handlerDef, request) { (routedHandler: Handler) =>
        routedHandler must haveInterface[Handler.Stage]
        val (preprocessedRequest, preprocessedHandler) = Handler.applyStages(request, routedHandler)
        preprocessedHandler must haveInterface[JavaHandler]
        preprocessedRequest.path must_== "/"
        preprocessedRequest.attrs(play.api.routing.Router.Attrs.HandlerDef) must_== handlerDef
      }
    }
  }
}

/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package controllers

import jakarta.inject.Inject
import play.api.mvc._

/**
 * Default actions ready to use as is from your routes file.
 *
 * Example:
 * {{{
 * GET   /google          controllers.Default.redirect(to = "http://www.google.com")
 * GET   /favicon.ico     controllers.Default.notFound
 * GET   /admin           controllers.Default.todo
 * GET   /xxx             controllers.Default.error
 * }}}
 */
class Default @Inject() () extends ControllerHelpers {
  private val Action = new ActionBuilder.IgnoringBody()(using controllers.Execution.trampoline)

  /**
   * Returns a 501 NotImplemented response.
   *
   * Example:
   * {{{
   * GET   /admin           controllers.Default.todo
   * }}}
   */
  def todo: Action[AnyContent] = TODO

  /**
   * Returns a 404 NotFound response.
   *
   * Example:
   * {{{
   * GET   /favicon.ico     controllers.Default.notFound
   * }}}
   */
  def notFound: Action[AnyContent] = Action {
    NotFound
  }

  /**
   * Returns a 303 SeeOther response.
   *
   * Example:
   * {{{
   * GET   /google          controllers.Default.redirect(to = "http://www.google.com")
   * }}}
   */
  def redirect(to: String): Action[AnyContent] = Action {
    Redirect(to)
  }

  /**
   * Returns a 500 InternalServerError response.
   *
   * Example:
   * {{{
   * GET   /xxx             controllers.Default.error
   * }}}
   */
  def error: Action[AnyContent] = Action {
    InternalServerError
  }
}

/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.filters.csp

import java.util.Locale

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import jakarta.inject._
import org.apache.pekko.util.ByteString
import play.api.http.HttpErrorHandler
import play.api.http.HttpErrorInfo
import play.api.http.Status
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.streams
import play.api.libs.streams.Accumulator
import play.api.mvc
import play.api.mvc._

/**
 * CSPReportAction exposes CSP content violations according to the [[https://www.w3.org/TR/CSP2/#violation-reports CSP reporting spec]]
 *
 * Be warned that Firefox and Chrome handle CSP reports very differently, and Firefox
 * omits [[https://mathiasbynens.be/notes/csp-reports fields which are in the specification]].  As such, many fields
 * are optional to ensure browser compatibility.
 *
 * To use this in a controller, add something like the following:
 *
 * {{{
 * class CSPReportController @Inject()(cc: ControllerComponents, cspReportAction: CSPReportActionBuilder) extends AbstractController(cc) {
 *
 *   private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
 *
 *   private def logReport(report: ScalaCSPReport): Unit = {
 *     logger.warn(s"violated-directive: \${report.violatedDirective}, blocked = \${report.blockedUri}, policy = \${report.originalPolicy}")
 *   }
 *
 *   val report: Action[ScalaCSPReport] = cspReportAction { request =>
 *     logReport(request.body)
 *     Ok("{}").as(JSON)
 *   }
 * }
 * }}}
 */
trait CSPReportActionBuilder extends ActionBuilder[Request, ScalaCSPReport]

class DefaultCSPReportActionBuilder @Inject() (parser: CSPReportBodyParser)(implicit ec: ExecutionContext)
    extends ActionBuilderImpl[ScalaCSPReport](parser)
    with CSPReportActionBuilder

trait CSPReportBodyParser extends play.api.mvc.BodyParser[ScalaCSPReport] with play.mvc.BodyParser[JavaCSPReport]

class DefaultCSPReportBodyParser @Inject() (parsers: PlayBodyParsers)(implicit ec: ExecutionContext)
    extends CSPReportBodyParser {
  private val impl: BodyParser[ScalaCSPReport] = BodyParser("cspReport") { request =>
    val contentType: Option[String] = request.contentType.map(_.toLowerCase(Locale.ENGLISH))
    contentType match {
      case Some("text/json") | Some("application/json") | Some("application/csp-report") =>
        parsers
          .tolerantJson(request)
          .map(_.flatMap { j =>
            (j \ "csp-report").validate[ScalaCSPReport] match {
              case JsSuccess(report, path) =>
                Right(report)
              case JsError(errors) =>
                Left(
                  createErrorResult(request, "Bad Request", "Could not parse CSP", Some(JsError.toJson(errors)))
                )
            }
          })

      case Some("application/x-www-form-urlencoded") =>
        // Really old webkit sends data as form data instead of JSON
        // https://www.tollmanz.com/content-security-policy-report-samples/
        // https://bugs.webkit.org/show_bug.cgi?id=61360
        // "document-url" -> "http://45.55.25.245:8123/csp?os=OS%2520X&device=&browser_version=3.6&browser=firefox&os_version=Yosemite",
        // "violated-directive" -> "object-src https://45.55.25.245:8123/"

        parsers
          .formUrlEncoded(request)
          .map(_.map { d =>
            val documentUri       = d("document-url").head
            val violatedDirective = d("violated-directive").head
            ScalaCSPReport(documentUri = documentUri, violatedDirective = violatedDirective)
          })

      case _ =>
        Accumulator.done {
          // https://tools.ietf.org/html/rfc7807
          val validTypes =
            Seq("application/x-www-form-urlencoded", "text/json", "application/json", "application/csp-report")
          val msg = s"Content type must be one of ${validTypes.mkString(",")} but was $contentType"
          Left(createErrorResult(request, "Unsupported Media Type", msg, statusCode = Status.UNSUPPORTED_MEDIA_TYPE))
        }
    }
  }

  @deprecated("Will be removed in an upcoming Play release", "2.9.0")
  protected def createBadResult(msg: String, statusCode: Int = Status.BAD_REQUEST): RequestHeader => Future[Result] = {
    request =>
      parsers.errorHandler
        .onClientError(
          request.addAttr(HttpErrorHandler.Attrs.HttpErrorInfo, HttpErrorInfo("csp-filter")),
          statusCode,
          msg
        )
        .map(_.as("application/problem+json"))
  }

  private def createErrorResult(
      request: RequestHeader,
      title: String,
      detail: String = "",
      errors: Option[JsObject] = None,
      statusCode: Int = Status.BAD_REQUEST
  ): Result = {
    Results
      .Status(statusCode)(
        Json.obj(
          "requestId" -> request.id,
          "title"     -> title,
          "status"    -> statusCode,
        ) ++ (if (detail.nonEmpty) {
                Json.obj("detail" -> detail)
              } else {
                Json.obj()
              }) ++
          errors.filter(_.fields.nonEmpty).map(_ => Json.obj("errors" -> errors)).getOrElse(Json.obj())
      )
      .as("application/problem+json")
  }

  import play.libs.streams.Accumulator
  import play.libs.F
  import play.mvc.Http
  import play.mvc.Result

  // Java API
  override def apply(request: Http.RequestHeader): Accumulator[ByteString, F.Either[Result, JavaCSPReport]] = {
    this
      .apply(request.asScala)
      .map { f =>
        f.fold[F.Either[Result, JavaCSPReport]](
          result => F.Either.Left(result.asJava),
          report => F.Either.Right(report.asJava)
        )
      }
      .asJava
  }

  // Scala API
  override def apply(rh: RequestHeader): streams.Accumulator[ByteString, Either[mvc.Result, ScalaCSPReport]] =
    impl.apply(rh)
}

/**
 * Result of parsing a CSP report.
 */
case class ScalaCSPReport(
    documentUri: String,
    violatedDirective: String,
    blockedUri: Option[String] = None,
    originalPolicy: Option[String] = None,
    effectiveDirective: Option[String] = None,
    referrer: Option[String] = None,
    disposition: Option[String] = None,
    scriptSample: Option[String] = None,
    statusCode: Option[Int] = None,
    sourceFile: Option[String] = None,
    lineNumber: Option[Long] = None,
    columnNumber: Option[Long] = None
) {
  def asJava: JavaCSPReport = {
    import scala.jdk.OptionConverters._
    new JavaCSPReport(
      documentUri,
      violatedDirective,
      blockedUri.toJava,
      originalPolicy.toJava,
      effectiveDirective.toJava,
      referrer.toJava,
      disposition.toJava,
      scriptSample.toJava,
      statusCode.toJava,
      sourceFile.toJava,
      lineNumber.toJava,
      columnNumber.toJava
    )
  }
}

object ScalaCSPReport {

  implicit val longOrStringToLongRead: Reads[Long] = {
    case JsString(s) =>
      try {
        JsSuccess(s.toLong)
      } catch {
        case _: NumberFormatException =>
          JsError(
            Seq(
              JsPath -> Seq(
                JsonValidationError("Could not parse line or column number in CSP Report; Inappropriate format")
              )
            )
          )
      }
    case JsNumber(s) =>
      JsSuccess(s.toLong)
    case _ =>
      JsError(
        Seq(
          JsPath -> Seq(
            JsonValidationError("Could not parse line or column number in CSP Report; Expected a number or a String")
          )
        )
      )
  }

  implicit val reads: Reads[ScalaCSPReport] =
    (__ \ "document-uri")
      .read[String]
      .and((__ \ "violated-directive").read[String])
      .and((__ \ "blocked-uri").readNullable[String])
      .and((__ \ "original-policy").readNullable[String])
      .and((__ \ "effective-directive").readNullable[String])
      .and((__ \ "referrer").readNullable[String])
      .and((__ \ "disposition").readNullable[String])
      .and((__ \ "script-sample").readNullable[String])
      .and((__ \ "status-code").readNullable[Int])
      .and((__ \ "source-file").readNullable[String])
      .and((__ \ "line-number").readNullable[Long](longOrStringToLongRead))
      .and((__ \ "column-number").readNullable[Long](longOrStringToLongRead))(ScalaCSPReport.apply)
}

import java.util.Optional

class JavaCSPReport(
    val documentUri: String,
    val violatedDirective: String,
    val blockedUri: Optional[String],
    val originalPolicy: Optional[String],
    val effectiveDirective: Optional[String],
    val referrer: Optional[String],
    val disposition: Optional[String],
    val scriptSample: Optional[String],
    val statusCode: Optional[Int],
    val sourceFile: Optional[String],
    val lineNumber: Optional[Long],
    val columnNumber: Optional[Long]
) {
  def asScala: ScalaCSPReport = {
    import scala.jdk.OptionConverters._
    ScalaCSPReport(
      documentUri,
      violatedDirective,
      blockedUri.toScala,
      originalPolicy.toScala,
      effectiveDirective.toScala,
      referrer.toScala,
      disposition.toScala,
      scriptSample.toScala,
      statusCode.toScala,
      sourceFile.toScala,
      lineNumber.toScala,
      columnNumber.toScala
    )
  }
}

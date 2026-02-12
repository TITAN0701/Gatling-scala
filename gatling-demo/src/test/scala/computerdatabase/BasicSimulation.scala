package computerdatabase

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Paths, StandardOpenOption }
import java.time.Instant
import scala.concurrent.duration._

class BasicSimulation extends Simulation {

  val runRepeat = sys.props.get("run.repeat").map(_.toInt).getOrElse(1)
  val runOnceSeconds = sys.props.get("run.onceSec").map(_.toInt).getOrElse(0)

  val baseUrl = "https://www.starlux-airlines.com"
  val request1Name = "request_1_cdn"

  val cdnLogPath = Paths.get("target", "gatling", "cdn.log").toAbsolutePath
  object CdnLogLock

  def jsonEscape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

  def writeCdnLog(line: String): Unit = CdnLogLock.synchronized {
    Files.createDirectories(cdnLogPath.getParent)
    Files.write(
      cdnLogPath,
      (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND
    )
  }

  val httpProtocol = http
    .baseUrl(baseUrl) // Here is the root for all relative URLs
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")

  val cdnHeaderNames = Seq(
    "Via",
    "Age",
    "X-Cache",
    "X-Cache-Hits",
    "X-Cache-Remote",
    "X-Served-By",
    "X-Iinfo",
    "X-CDN",
    "CF-Cache-Status",
    "CF-Ray",
    "X-Amz-Cf-Id",
    "X-Amz-Cf-Pop",
    "X-Akamai-Transformed"
  )

  val request1Warm = http("request_1_warm").get("/")

  def cdnKey(headerName: String): String =
    s"cdn_${headerName.replace('-', '_')}"

  val request1WithCdnChecks =
    cdnHeaderNames.foldLeft(http(request1Name).get("/")) { (r, h) =>
      r.check(header(h).find.optional.saveAs(cdnKey(h)))
    }
      .check(status.saveAs("http_status"))
      .check(responseTimeInMillis.saveAs("resp_time_ms"))

  val baseChain = exec(request1Warm)
    .exec(request1WithCdnChecks)
    .exec { session =>
      val hits = cdnHeaderNames.flatMap { h =>
        session(cdnKey(h)).asOption[String].map(v => s"$h=$v")
      }

      def intFrom(s: String): Option[Int] =
        """(\d+)""".r.findFirstIn(s).flatMap(v => scala.util.Try(v.toInt).toOption)

      val ageHit = session("cdn_Age").asOption[String].flatMap(intFrom).exists(_ > 0)
      val xCacheHit = session("cdn_X-Cache").asOption[String].exists(_.toUpperCase.contains("HIT"))
      val xCacheHitsHit = session("cdn_X-Cache-Hits").asOption[String].flatMap(intFrom).exists(_ > 0)
      val cfCacheHit = session("cdn_CF-Cache-Status").asOption[String].exists(_.toUpperCase.contains("HIT"))
      val cacheHit = ageHit || xCacheHit || xCacheHitsHit || cfCacheHit
      val impervaSeen =
        session.contains(cdnKey("X-Iinfo")) || session.contains(cdnKey("X-CDN"))
      val cdnInPath = impervaSeen || hits.nonEmpty
      val strictHit = cdnInPath && cacheHit

      val statusValue = session("http_status").asOption[Int]
      val responseTimeMs = session("resp_time_ms").asOption[Int]

      val logLine = {
        val headersJson =
          hits.map(h => "\"" + jsonEscape(h) + "\"").mkString("[", ",", "]")
        val statusJson = statusValue.map(_.toString).getOrElse("null")
        val responseTimeJson = responseTimeMs.map(_.toString).getOrElse("null")
        s"""{"ts":"${Instant.now}","request":"${jsonEscape(request1Name)}","url":"${jsonEscape(baseUrl + "/")}","status":$statusJson,"response_time_ms":$responseTimeJson,"cdn_in_path":$cdnInPath,"cdn_cache_hit":$cacheHit,"strict_hit":$strictHit,"headers":$headersJson}"""
      }

      if (!strictHit) {
        writeCdnLog(logLine)
      }

      val nextSession =
        if (!session.contains("cdn_logged")) {
          System.out.println("[CDN] " + (if (hits.isEmpty) "NONE" else hits.mkString(", ")))
          System.out.println("[CDN] cdn_in_path=" + cdnInPath + ", cdn_cache_hit=" + cacheHit + ", strictHit=" + strictHit)
          System.out.flush()
          session.set("cdn_logged", true)
        } else {
          session
        }

      if (!strictHit) nextSession.markAsFailed else nextSession
    }
    .pause(7) // Note that Gatling has recorded real time pauses
    .exec(http("request_2")
      .get("/computers?f=macbook"))
    .pause(2)
    .exec(http("request_3")
      .get("/computers/6"))
    .pause(3)
    .exec(http("request_4")
      .get("/"))
    .pause(2)
    .exec(http("request_5")
      .get("/computers?p=1"))
    .pause(670.milliseconds)
    .exec(http("request_6")
      .get("/computers?p=2"))
    .pause(629.milliseconds)
    .exec(http("request_7")
      .get("/computers?p=3"))
    .pause(734.milliseconds)
    .exec(http("request_8")
      .get("/computers?p=4"))
    .pause(5)
    .exec(http("request_9")
      .get("/computers/new"))
    .pause(1)
    .exec(http("request_10") // Here's an example of a POST request
      .post("/computers")
      .formParam("""name""", """Beautiful Computer""") // Note the triple double quotes: used in Scala for protecting a whole chain of characters (no need for backslash)
      .formParam("""introduced""", """2012-05-30""")
      .formParam("""discontinued""", """""")
      .formParam("""company""", """37"""))

  val pacedChain =
    if (runOnceSeconds > 0) baseChain.pace(runOnceSeconds.seconds) else baseChain

  val scn = scenario("Scenario Name") // A scenario is a chain of requests and pauses
    .exec(
      if (runRepeat > 1) repeat(runRepeat) { pacedChain } else pacedChain
    )

  setUp(scn.inject(atOnceUsers(1)).protocols(httpProtocol))
}

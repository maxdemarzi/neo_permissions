package simulations

import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import akka.util.duration._
import bootstrap._
import util.parsing.json.JSONArray


class TestPermissions extends Simulation {
  val httpConf = httpConfig
    .baseURL("http://localhost:7474")
    .acceptHeader("application/json")
    // Uncomment to see Requests
    //    .requestInfoExtractor(request => {
    //    println(request.getStringData)
    //    Nil
    //  })
    // Uncomment to see Response
    //    .responseInfoExtractor(response => {
    //    println(response.getResponseBody)
    //    Nil
    //  })
    .disableResponseChunksDiscarding

  val testfile = csv("test-data.txt").map {
    _.map {

      case (key, value) => key match {
        case "results" => (key, value.toInt)
        case _ => (key, value)
      }
    }
  }.toArray.circular



  val scn = scenario("Permissions via Unmanaged Extension")
    .feed(testfile)
    .during(10) {
    exec(
      http("Post Permissions Request")
        .post("/example/service/permissions")
        .body("${userid},${documentids}")
        .header("Content-Type", "application/text")
        .check(status.is(200))
        .check(regex("[\"][a-f0-9-]*[\"]")
        .count.is(session => session.getTypedAttribute[Int]("results"))
      ))
      .pause(0 milliseconds, 5 milliseconds)

  }


  setUp(
    scn.users(20).ramp(10).protocolConfig(httpConf)
  )
}

package simulations

import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import akka.util.duration._
import bootstrap._
import util.parsing.json.{JSON, JSONArray}


class RandomPermissions extends Simulation {
  val httpConf = httpConfig
    .baseURL("http://localhost:7474")
    .acceptCharsetHeader("utf-8")
    .acceptHeader("application/json")
    .acceptEncodingHeader("gzip, deflate")
    .disableFollowRedirect
    .disableWarmUp
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

  val writer = {
    val fos = new java.io.FileOutputStream("src/test/resources/data/test-data.txt")
    new java.io.PrintWriter(fos,true)
  }

  val rnd = new scala.util.Random
  val usersRange   = 1 to 3000
  val documentRange = 3101 to 1003100

  var usersForSimulation = List.fill(200)(usersRange(rnd.nextInt(usersRange.length)))
  var documentsForSimulation = List.fill(1000)(documentRange(rnd.nextInt(documentRange.length)))

  // Go fetch the users from the database.
  val fetchSomeUserIds = """START user=node({ids}) RETURN collect(user.unique_id) as uids"""
  val userPostBody = """{"query": "%s", "params": {"ids": %s}}""".format(fetchSomeUserIds, JSONArray.apply(usersForSimulation).toString())
  val userFetcher = exec(
    http("Get some random people")
      .post("/db/data/cypher")
      .header("X-Stream", "true")
      .body(userPostBody)
      .asJSON
      .check(status.is(200))
      .check(bodyString.transform(mybody => {
      val map:Map[String,_] = JSON.parseFull(mybody) match {
        case Some(map:Map[String,_]) => map
        case _ => Map()
      }

      val listone:List[List[List[String]]] = map.get("data") match {
        case Some(results:List[List[List[String]]]) => results
        case _ => List()
      }

      listone.head.head
    }).saveAs("users")))

  // Go fetch the documents from the database.
  val fetchSomeDocumentIds = """START doc=node({ids}) RETURN collect(doc.unique_id) as uids"""
  val documentPostBody = """{"query": "%s", "params": {"ids": %s}}""".format(fetchSomeDocumentIds, JSONArray.apply(documentsForSimulation).toString())
  val documentFetcher = exec(
    http("Get some random documents")
      .post("/db/data/cypher")
      .header("X-Stream", "true")
      .body(documentPostBody)
      .asJSON
      .check(status.is(200))
      .check(bodyString.transform(mybody => {
      val map:Map[String,_] = JSON.parseFull(mybody) match {
        case Some(map:Map[String,_]) => map
        case _ => Map()
      }

      val listone:List[List[List[String]]] = map.get("data") match {
        case Some(results:List[List[List[String]]]) => results
        case _ => List()
      }

      listone.head.head
    }).saveAs("documents")))


  // Randomly choose a user from the Nodes that we fetched.
  val chooseRandomPerson = exec((session) => {
    val users:List[String] = session.getTypedAttribute("users")
    val user: String = users(rnd.nextInt(users length))
    session.setAttribute("user_id", user)
  })

  val chooseRandomDocuments = exec((session) => {
    val documents:List[String] = session.getTypedAttribute("documents")
    session.setAttribute("doc_ids", documents.take(1000).mkString(" "))
  })

  val checkPermissions = during(10) {
    exec(chooseRandomPerson, chooseRandomDocuments)
    .exec(
      http("Post Permissions Request")
        .post("/example/service/permissions")
        .body("${user_id},${doc_ids}")
        .header("Content-Type", "application/text")
        .check(status.is(200))
        .check(regex("[\"][a-f0-9-]*[\"]")
        .exists)
        .check(regex("[\"][a-f0-9-]*[\"]").count.saveAs("count"))
      )
    .exec((s: Session) => {
      writer.println(s.getAttribute("count") + "," + s.getAttribute("user_id") + "," + s.getAttribute("doc_ids"))
      s
    })
      .pause(0 milliseconds, 5 milliseconds)
  }

  val createFile = scenario("Prepare test-data.txt file").
      exec((s: Session) => {
       writer.println("results,userid,documentids")
       s
       })

  val scn = scenario("Permissions via Unmanaged Extension")
    .exec(userFetcher, documentFetcher, checkPermissions)


  setUp(
    createFile.users(1),
    scn.users(10).ramp(10).protocolConfig(httpConf)
  )
}

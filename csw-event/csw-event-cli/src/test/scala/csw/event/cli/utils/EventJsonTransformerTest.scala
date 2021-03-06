package csw.event.cli.utils

import org.scalatest.{BeforeAndAfterEach, FunSuite, Matchers}
import play.api.libs.json.{JsObject, Json}

import scala.io.Source

class EventJsonTransformerTest extends FunSuite with Matchers with BeforeAndAfterEach {

  private val event1Json = Json.parse(Source.fromResource("seedData/event1.json").mkString).as[JsObject]

  test("should be able to get entire event json when no paths are specified") {

    val transformedEventJson = EventJsonTransformer.transform(event1Json, Nil)
    transformedEventJson shouldBe event1Json
  }

  test("should be able to get top level non struct key from json") {

    val expectedEventJson =
      Json.parse(Source.fromResource("json/top_level_non_struct_key.json").mkString).as[JsObject]

    val paths                = List("epoch")
    val transformedEventJson = EventJsonTransformer.transform(event1Json, paths)
    transformedEventJson shouldBe expectedEventJson
  }

  test("should be able to get top level partial struct key from json") {

    val expectedEventJson =
      Json.parse(Source.fromResource("json/top_level_partial_struct_key.json").mkString).as[JsObject]
    val paths                = List("struct-1")
    val transformedEventJson = EventJsonTransformer.transform(event1Json, paths)
    transformedEventJson shouldBe expectedEventJson
  }

  test("should be able to get specified paths two levels deep in event in json format") {

    val expectedEventJson    = Json.parse(Source.fromResource("json/get_path_2_levels_deep.json").mkString).as[JsObject]
    val paths                = List("struct-1/ra")
    val transformedEventJson = EventJsonTransformer.transform(event1Json, paths)
    transformedEventJson shouldBe expectedEventJson
  }

  test("should be able to get multiple specified paths in event in json format") {

    val expectedEventJson    = Json.parse(Source.fromResource("json/get_multiple_paths.json").mkString).as[JsObject]
    val paths                = List("struct-1/ra", "epoch")
    val transformedEventJson = EventJsonTransformer.transform(event1Json, paths)
    transformedEventJson shouldBe expectedEventJson
  }

  test("should be able to get specified paths for multiple events in json format") {

    val event1Json         = Json.parse(Source.fromResource("seedData/event1.json").mkString).as[JsObject]
    val event2Json         = Json.parse(Source.fromResource("seedData/event2.json").mkString).as[JsObject]
    val expectedEvent1Json = Json.parse(Source.fromResource("json/get_multiple_events1.json").mkString).as[JsObject]
    val expectedEvent2Json = Json.parse(Source.fromResource("json/get_multiple_events2.json").mkString).as[JsObject]

    val transformedEventJson1 = EventJsonTransformer.transform(event1Json, List("struct-1/ra"))
    val transformedEventJson2 = EventJsonTransformer.transform(event2Json, List("struct-2/struct-1/ra"))
    transformedEventJson1 shouldBe expectedEvent1Json
    transformedEventJson2 shouldBe expectedEvent2Json
  }

  test("should be able to get full struct if both partial struct path and full path are given") {

    val event1Json         = Json.parse(Source.fromResource("seedData/event1.json").mkString).as[JsObject]
    val expectedEvent1Json = Json.parse(Source.fromResource("json/top_level_partial_struct_key.json").mkString).as[JsObject]

    val transformedEventJson1 = EventJsonTransformer.transform(event1Json, List("struct-1/ra", "struct-1"))
    transformedEventJson1 shouldBe expectedEvent1Json
  }
}

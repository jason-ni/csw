package csw.services.event.internal.redis

import akka.stream.scaladsl.{Keep, Sink, Source}
import com.github.sebruck.EmbeddedRedis
import csw.messages.ccs.events.{Event, EventKey, EventName, SystemEvent}
import csw.messages.params.models.{Id, Prefix}
import csw.services.event.helpers.PortHelper
import csw.services.event.helpers.TestFutureExt.RichFuture
import csw.services.event.internal.Wiring
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import redis.embedded.RedisServer

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.Await
import scala.concurrent.duration.DurationDouble

class RedisPubSubTest extends FunSuite with Matchers with BeforeAndAfterAll with EmbeddedRedis {

  private val port: Port = PortHelper.freePort
  private val wiring     = new Wiring(port)

  import wiring._
  lazy val redis: RedisServer = RedisServer.builder().setting("bind 127.0.0.1").port(port).build()

  redis.start()

  override def afterAll(): Unit = {
    Await.result(redisClient.shutdownAsync().toCompletableFuture.toScala, 5.seconds)
    Await.result(actorSystem.terminate(), 5.seconds)
    redis.stop()
  }

  test("pub-sub") {
    val prefix             = Prefix("test.prefix")
    val eventName          = EventName("system")
    val event              = SystemEvent(prefix, eventName)
    val eventKey: EventKey = event.eventKey

    val (subscription, seqF) = redisSubscriber.subscribe(Set(eventKey)).toMat(Sink.seq)(Keep.both).run()
    Thread.sleep(1000)

    redisPublisher.publish(event).await
    subscription.unsubscribe().await
    seqF.await shouldBe List(event)
  }

  test("independent subscriptions") {
    val prefix        = Prefix("test.prefix")
    val prefix2       = Prefix("test.prefix2")
    val eventName     = EventName("system")
    val event: Event  = SystemEvent(prefix, eventName)
    val event2: Event = SystemEvent(prefix2, eventName)

    val (subscription, seqF) = redisSubscriber.subscribe(Set(event.eventKey)).toMat(Sink.seq)(Keep.both).run()
    Thread.sleep(1000)
    redisPublisher.publish(event).await

    val (subscription2, seqF2) = redisSubscriber.subscribe(Set(event2.eventKey)).toMat(Sink.seq)(Keep.both).run()
    Thread.sleep(1000)
    redisPublisher.publish(event2).await

    subscription.unsubscribe().await
    subscription2.unsubscribe().await

    seqF.await shouldBe List(event)
    seqF2.await shouldBe List(event2)
  }

  ignore("multiple publishers") {
    val prefix    = Prefix("test.prefix")
    val eventName = EventName("system")

    def event: Event = SystemEvent(prefix, eventName)

    val eventKey: EventKey = event.eventKey

    redisSubscriber.subscribe(Set(eventKey)).runForeach { x =>
      val begin = x.eventTime.time.toEpochMilli
      println(System.currentTimeMillis() - begin)
    }

    Thread.sleep(10)

    redisPublisher.publish(Source.fromIterator(() => Iterator.continually(event)).map(x => { println(s"from 1 -> $x"); x }))
    redisPublisher
      .publish(Source.fromIterator(() => Iterator.continually(event)).map(x => { println(s"from 2            -> $x"); x }))
      .await
  }

  test("throughput") {
    val subscriber = redisSubscriber
    val publisher  = redisPublisher

    val prefix    = Prefix("test.prefix")
    val eventName = EventName("system")

    def event(x: Int): Event = SystemEvent(prefix, eventName).copy(eventId = Id(x.toString))

    val eventKey: EventKey = event(0).eventKey

    val (sub, seqF) = subscriber.subscribe(Set(eventKey)).map(x => { println(x); x }).toMat(Sink.seq)(Keep.both).run()

    Thread.sleep(100)

    val limit = 10

    val stream = Source(1 to limit).map(event)

    publisher.publish(stream).await
//    stream.mapAsync(10)(publisher.publish).runForeach(_ => ()).await

//    val queue = publisher.queue()
//    stream.mapAsync(1)(queue.offer).runForeach(_ => ()).await
//    queue.complete()
//    queue.watchCompletion().await

    sub.unsubscribe().await
    seqF.await.map(_.eventId.id.toInt) shouldBe (1 to limit).toList
  }

  ignore("latency") {
    val prefix    = Prefix("test.prefix")
    val eventName = EventName("system")

    def event: Event = SystemEvent(prefix, eventName)

    val eventKey: EventKey = event.eventKey
    redisSubscriber.subscribe(Set(eventKey)).runForeach { x =>
      val begin = x.eventTime.time.toEpochMilli
      println(System.currentTimeMillis() - begin)
    }

    Thread.sleep(10)

    Source
      .fromIterator(() => Iterator.continually(event))
      .mapAsync(1)(redisPublisher.publish)
      .runForeach(_ => ())
      .await
  }

}
package csw.time.core.models

import java.time.{Duration, Instant}

import csw.time.clock.natives.models.TMTClock
import org.scalatest.Matchers._
import org.scalatest.{BeforeAndAfterAll, FunSuite}

import scala.concurrent.duration.DurationDouble

class TMTTimeTest extends FunSuite with BeforeAndAfterAll {
  private val TaiOffset = 37

  override protected def beforeAll(): Unit = TMTClock.clock.setTaiOffset(TaiOffset)

  test("should get utc time") {
    val utcTime        = UTCTime.now()
    val fixedInstant   = Instant.now()
    val expectedMillis = fixedInstant.toEpochMilli

    utcTime.value.toEpochMilli.toDouble shouldEqual expectedMillis.toDouble +- 5
  }

  test("should convert utc to tai") {
    val utcTime = UTCTime.now()
    val taiTime = utcTime.toTAI
    Duration.between(utcTime.value, taiTime.value).getSeconds shouldEqual TaiOffset
  }

  test("should give time duration between given timestamp and current time") {
    val instant          = Instant.now().plusSeconds(1)
    val expectedDuration = 1.second.toNanos +- 10.millis.toNanos

    UTCTime(instant).durationFromNow.toNanos shouldBe expectedDuration
  }
}

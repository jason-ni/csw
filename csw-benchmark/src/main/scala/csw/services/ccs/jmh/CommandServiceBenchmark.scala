package csw.services.ccs.jmh

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Scheduler}
import akka.util
import com.typesafe.config.ConfigFactory
import csw.messages.ccs.commands
import csw.messages.ccs.commands.{CommandName, CommandResponse, ComponentRef}
import csw.messages.params.models.Prefix
import csw.services.ccs.jmh.BenchmarkHelpers.spawnStandaloneComponent
import csw.services.location.commons.ClusterAwareSettings
import csw.services.logging.internal.LoggingSystem
import csw.services.logging.scaladsl.{Logger, LoggerFactory, LoggingSystemFactory}
import org.openjdk.jmh.annotations._

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS, batchSize = 1)
class CommandServiceBenchmark {

  implicit var actorSystem: ActorSystem = _
  implicit var timeout: util.Timeout    = _
  implicit var scheduler: Scheduler     = _
  var setupCommand: commands.Setup      = _
  var componentRef: ComponentRef        = _
  var log: Logger                       = _
  var loggingSystem: LoggingSystem      = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    actorSystem = ClusterAwareSettings.onPort(3552).system

    loggingSystem = LoggingSystemFactory.start("CommandBench", "1.0", "localhost", actorSystem)
    log = new LoggerFactory("Test").getLogger

    componentRef = spawnStandaloneComponent(actorSystem, ConfigFactory.load("standalone.conf"))

    setupCommand = commands.Setup(Prefix("wfos.blue.filter"), CommandName("jmh"), None)
    timeout = util.Timeout(5.seconds)
    scheduler = actorSystem.scheduler
  }

  @TearDown(Level.Trial)
  def teardown(): Unit = {
    Await.result(loggingSystem.stop, 10.seconds)
    actorSystem.terminate()
    Await.ready(actorSystem.whenTerminated, 15.seconds)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  def commandThroughput(): CommandResponse = {
    log.info(s"Sending command : ${System.nanoTime()}")
    val commandResponse = Await.result(componentRef.submit(setupCommand), 5.seconds)
    commandResponse
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def commandLatency(): CommandResponse = {
    log.info(s"Sending command : ${System.nanoTime()}")
    val commandResponse = Await.result(componentRef.submit(setupCommand), 5.seconds)
    commandResponse
  }
}

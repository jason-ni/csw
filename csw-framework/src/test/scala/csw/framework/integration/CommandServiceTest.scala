package csw.framework.integration

import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.framework.FrameworkTestWiring
import csw.params.commands.CommandResponse.{Completed, SubmitResponse}
import csw.params.commands.{CommandName, CommandResponse, ControlCommand, Setup}
import csw.params.core.models.{Id, ObsId, Prefix}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationLong

class CommandServiceTest extends FunSuite with Matchers with MockitoSugar with BeforeAndAfterAll {

  private val testWiring = new FrameworkTestWiring()

  import testWiring._

  override protected def afterAll(): Unit = shutdown()

  val prefix = Prefix("wfos.blue.filter")
  val obsId  = ObsId("Obs001")

  implicit val timeout: Timeout = 5.seconds

  val moveCmd = CommandName("move")
  val initCmd = CommandName("init")

  /*
    val hcdLocF =
      locationService.resolve(
        AkkaConnection(ComponentId("Test_Component_Running_Long_Command", ComponentType.HCD)),
        5.seconds
      )
   */
  //val hcdLocation: AkkaLocation = Await.result(hcdLocF, 10.seconds).get
  //val hcdComponent              = new CommandService(hcdLocation)

  val setupAssembly1 = Setup(prefix, moveCmd, Some(obsId))
  val setupAssembly2 = Setup(prefix, initCmd, Some(obsId))
  //val setupAssembly3 = Setup(prefix, invalidCmd, Some(obsId))
  //val setupHcd1      = Setup(prefix, shortRunning, Some(obsId))
  //val setupHcd2      = Setup(prefix, mediumRunning, Some(obsId))
  //val setupHcd3      = Setup(prefix, failureAfterValidationCmd, Some(obsId))

  def submit(controlCommand: ControlCommand)(implicit timeout: Timeout): Future[SubmitResponse] = {
    Future(Completed(Id()))
  }

  /**
   * Submits the given setups, one after the other, and returns a future list of command responses.
   * @param setups the setups to submit
   * @param assembly the assembly to submit the setups to
   * @return future list of responses
   */
  private def submitAll(setups: List[Setup], assembly: CommandService): Future[List[SubmitResponse]] = {
    Source(setups)
      .mapAsync(1)(assembly.submit)
      .map { response =>
        if (CommandResponse.isNegative(response))
          throw new RuntimeException(s"Command failed: $response")
        response
      }
      .toMat(Sink.seq)(Keep.right)
      .run()
      .map(_.toList)
  }

  test("test submit all 1") {
    // start component in standalone mode
    //val wiring: FrameworkWiring = FrameworkWiring.make(testActorSystem, mock[RedisClient])
    //Standalone.spawn(ConfigFactory.load("standalone.conf"), wiring)

    //val akkaConnection                = AkkaConnection(ComponentId("IFS_Detector", HCD))

    // verify component gets registered with location service
    //val eventualLocation = seedLocationService.resolve(akkaConnection, 5.seconds)
    //val maybeLocation    = Await.result(eventualLocation, 5.seconds)

    //maybeLocation.isDefined shouldBe true
    //val resolvedAkkaLocation = maybeLocation.get
    //resolvedAkkaLocation.connection shouldBe akkaConnection
    //val x = submitAll()
    val setups = List(setupAssembly1, setupAssembly2)

    val mcs: CommandService = mock[CommandService]
    //when(submitAll(eq(setups), mcs)) //.thenReturn(Future(Completed(Id())))

    val rr = submitAll(setups, mcs)

    //val cs = new CommandService(resolvedAkkaLocation)
    //val x = submitAll(List(setupAssembly1, setupAssembly2), )

  }

}
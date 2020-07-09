package part4_techniques

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import scala.concurrent.Future

object IntegratingWithExternalServices extends App {

  implicit val system = ActorSystem("IntegratingWithExternalService")
  implicit val materializer = ActorMaterializer()

  // import system.dispatcher // not recommended in practice for mapAsync
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  def genericService[A, B](element: A): Future[B] = ???

  // example: simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(List(
    PagerEvent("AkkaInfra", "Infrastructure broken", new Date),
    PagerEvent("FastDataPipeline", "Illegal elements in the data pipeline", new Date),
    PagerEvent("AkkaInfra", "A service stopped responding", new Date),
    PagerEvent("SuperFrontend", "A button doesn't work", new Date)
  ))

  // original solution: using a simple service
  object PagerService {
    private val engineers = List("Daniel", "John", "Jim")
    private val emails = Map(
      "Daniel" -> "daniel@rockthejvm.com",
      "John" -> "john@rockthejvm.com",
      "Jim" -> "jim@rockthejvm.com"
    )

    def processEvent(pagerEvent: PagerEvent) = Future {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)

      // return the email that was paged
      engineerEmail
    }
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfra")
  val pagedEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => PagerService.processEvent(event))
  // guarantees the relative order of elements
  val pagedEmailsSink = Sink.foreach[String](email => println(s"Successfully sent notification to $email"))

//  pagedEngineerEmails.to(pagedEmailsSink).run()


  // solution: using actors
  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("Daniel", "John", "Jim")
    private val emails = Map(
      "Daniel" -> "daniel@rockthejvm.com",
      "John" -> "john@rockthejvm.com",
      "Jim" -> "jim@rockthejvm.com"
    )

    private def processEvent(pagerEvent: PagerEvent) = {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)

      // return the email that was paged
      engineerEmail
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }

  import akka.pattern.ask
  import scala.concurrent.duration._
  implicit val timeout = Timeout(4 seconds)
  val pagerActor = system.actorOf(Props[PagerActor], "pagerActor")
  val alternativePagedEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])
  alternativePagedEngineerEmails.to(pagedEmailsSink).run()

  // do not confused mapAsync with async (ASYNC boundary)
}

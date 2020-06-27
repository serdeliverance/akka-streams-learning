package part2_primer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

object BackpressureBasics extends App {

  implicit val system = ActorSystem("BackpressureBasics")
  implicit val  materializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    // simulate a long processing
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

    // fastSource.to(slowSink).run()
    // operators fusion => runs on the same actor => not backpressure

  fastSource.async.to(slowSink).run()
  // backpressure

  // other example
  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming : $x")
    x + 1
  }

  fastSource.async
    .via(simpleFlow).async
    .to(slowSink)
//    .run()

  /*
    reactions to backpressure(in order):
      - try to slow down if possible
      - buffer elements until there's more demand
      - drop down elements from the buffer if it overflows
      - tear down/kill the whole stream (failure)
   */

  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)
  fastSource.async
    .via(bufferedFlow).async
    .to(slowSink)
    .run()
}

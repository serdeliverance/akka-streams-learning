package part2_primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App {

  implicit val system = ActorSystem("FirstPrinciples")
  implicit val materializer = ActorMaterializer()

  // sources
  val source = Source(1 to 10)

  // sink
  val sink = Sink.foreach[Int](println)

  val graph = source.to(sink)

  // flows transform elements
  val flow = Flow[Int].map(x => x + 1)
  val sourceWithFlow = source.via(flow)

  val flowWithSink = flow.to(sink)

  // sourceWithFlow.to(sink).run()

  // nulls are not allowed
//  val illegalSource = Source.single[Int](null)
//  illegalSource.to(Sink.foreach(println)).run()

  // various kinds of source
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1,2,3))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(Stream.from(1))

  import scala.concurrent.ExecutionContext.Implicits.global

  val futureSource = Source.fromFuture(Future(42))    // Source emits the value when future is completed

  // sinks
  val theMostBoredSink = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // receives the head and then closes the stream
  val foldSink = Sink.fold[Int, Int](0)((a, b) => a + b)

  // flows - usually mapped to collection operators
  val mapFlow = Flow[Int].map(x => 2 * x)
  val takeFlow = Flow[Int].take(5)
  // drop, filter
  // NOT have flatMap

  // source -> flow -> ... -> sink
  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
  doubleFlowGraph.run()

  // syntactic sugars
  val mapSource = Source(1 to 10).map(x => x * 2) // Source(1 to 10).via(Flow[Int].map(x => x * 2))

  // run streams directly
  // mapSource.runForeach(println) // mapSource.to(Sink.foreach[Int](println)).run()

  // OPERATORS = components

  /**
   * Exercise: create a stream that takes the names of persons, then you will keep the first 2 names
   * with length > 5 characters
   */
  val names = List("pepe", "ale", "francisco", "julian", "agos")
  // my solution:
  // Source(names).filter(_.length > 5).take(2).runForeach(println)

  // teacher solution:
  val nameSource = Source(names)
  val longNameFlow = Flow[String].filter(name => name.length > 5)
  val limitFlow = Flow[String].take(2)
  val nameSink = Sink.foreach[String](println)

  nameSource.via(longNameFlow).via(limitFlow).to(nameSink).run()
}

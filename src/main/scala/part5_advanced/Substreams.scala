package part5_advanced

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.util.{Failure, Success}

object Substreams extends App {

  implicit val system = ActorSystem("Substreams")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  // 1 - grouping a stream by a certain function
  val wordSource = Source(List("Akka", "is", "amazing", "learning", "substreams"))
  val groups = wordSource.groupBy(30, word => if (word.isEmpty) '\0' else word.toLowerCase().charAt(0))

  groups.to(Sink.fold(0)((count, word) => {
    val newCount = count + 1
    println(s"I just received $word, count is $newCount")
    newCount
  }))
    .run()

  // 2 - merge substreams back
  val textSource = Source(List(
    "I Love Akka Streams",
    "this is amazing",
    "learning from Rock the JVM"
  ))

  // creating substreams and then merge them
  val totalCharCountFuture = textSource.groupBy(2, string => string.length % 2)
    .map(_.length) // do your expensive computation here
    .mergeSubstreamsWithParallelism(2)  // it merge the substream into one stream
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  totalCharCountFuture.onComplete {
    case Success(value) => println(s"Total char count: $value")
    case Failure(ex) => println(s"Char computation failed: $ex")
  }

  // 3 - splitting a stream into substreams, when condition is met
  val text = "I love Akka Streams\n" +
    "this is amazing\n" +
    "learning from Rock the JVM\n"

  val anotherCharCountFuture = Source(text.toList)
    .splitWhen(c => c == '\n')  // splitting the stream when condition is met
    .filter(_ != '\n')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  anotherCharCountFuture.onComplete {
    case Success(value) => println(s"Total char count alternative: $value")
    case Failure(ex) => println(s"Char computation failed: $ex")
  }

  // 4 - flattening
  val simpleSource = Source(1 to 5)
  simpleSource.flatMapConcat(x => Source(x to (3 * x))).runWith(Sink.foreach(println))
}

package part2_primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import playground.MaterializingStreamsReview.system

import scala.util.{Failure, Success}

object MaterializingStreams extends App {

  implicit val system = ActorSystem("MaterializingStreams")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
  // val materializedValue = simpleGraph.run()

  // after completing the reduce it exposes a materialized value of type Future[Int]

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int]((a,b) => a + b)
  val sumFuture = source.runWith(sink)

  import system.dispatcher

  sumFuture.onComplete {
    case Success(value) => println(s"The sum of all elements is $value")
    case Failure(ex) => println(s"Error. the sum could not be computed: $ex")
  }


  // choosing value to materialize

  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(x => x + 1)
  val simpleSink = Sink.foreach[Int](println)

  // how to choose which value to materialize?

  simpleSource.viaMat(simpleFlow)((sourceMat, flowMat) => flowMat)
  /*
    Source>>viaMat recibes a combine function to decide which value to materialize from the
    two connected components
   */

  //  simpleSource.viaMat(simpleFlow)((sourceMat, flowMat) => flowMat)
  simpleSource.viaMat(simpleFlow)(Keep.right)   // it is the same as the line above

  // now we choose to materialize the most right component (simpleSink)
  // So, when running this graph it will return Future[Done]
  val graph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)

  graph.run().onComplete {
    case Success(_) => println("stream processing finished")
    case Failure(ex) => println(s"stream processinf failed: $ex")
  }

  // syntatic sugars for materializing

  // forward
  Source(1 to 10).runWith(Sink.reduce[Int](_ + _))  // source.to(Sink.reduce)(Keep.right)
  Source(1 to 10).reduce(_ + _) // same

  // backwards
  Sink.foreach[Int](println).runWith(Source.single(42)) // source(..).to(sink...).run()
  // source(..).to(sink...) always materialized the left value

  // both ways
  Flow[Int].map(x => 2 * x).runWith(simpleSource, simpleSink)

  /**
   * - return the last element out of a source (use Sink.last)
   * - compute the total word count out of a stream of sentences
   *  - map, fold, reduce
   */
  val f1 = Source(1 to 10).toMat(Sink.last)(Keep.right).run()
  val f2 = Source(1 to 10).runWith(Sink.last)

  val sentenceSource = Source(List("hola", "materializing", "ble"))
  val wordCountSink = Sink.fold[Int, String](0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)
  val g1 = sentenceSource.toMat(wordCountSink)(Keep.right).run()
  val g2 = sentenceSource.runWith(wordCountSink)
  val g3 = sentenceSource.runFold(0)((currentWordCount, newSentence) => currentWordCount + newSentence.split(" ").length)
}

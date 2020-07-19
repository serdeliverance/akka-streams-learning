package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

import scala.collection.mutable
import scala.util.Random

object CustomOperators extends App {

  implicit val system = ActorSystem("CustomOperators")
  implicit val materializer = ActorMaterializer()

  // A custom source which emits random numbers until canceled
  // step 0: define the shape
  class RandomNumberGenerator(max: Int) extends GraphStage[SourceShape[Int]] {

    // step 1: define the ports and the component-specific members
    val outPort = Outlet[Int]("randomGenerator")
    val random = new Random()

    // step 2: construct the new shape
    override def shape: SourceShape[Int] = SourceShape(outPort)

    // step 3: create the logic
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      // step 4:
      // implement

      setHandler(outPort, new OutHandler {
        // when there is demand from downstream
        override def onPull(): Unit = {
          // emit a new element
          val nextNumber = random.nextInt(max)
          // push it out of the outPort
          push(outPort, nextNumber)
        }
      })
    }
  }

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
//  randomGeneratorSource.runWith(Sink.foreach(println))

  // A custom sink that prints elements in batches of a given size

  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]] {

    val inPort = Inlet[Int]("batcher")

    override def shape: SinkShape[Int] = SinkShape[Int](inPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      override def preStart(): Unit = {
        pull(inPort)  // signalling demand for upstream element
      }

      // mutable state
      val batch = new mutable.Queue[Int]

      setHandler(inPort, new InHandler {
        // when upstream want to send me an element
        override def onPush(): Unit = {
          val nextElement = grab(inPort)
          batch.enqueue(nextElement)

          if (batch.size >= batchSize) {
            println("New batch: " + batch.dequeueAll(_ => true).mkString("[", ", ", "]"))
          }

          pull(inPort) // send demand upstream
        }

        override def onUpstreamFinish(): Unit = {
          if (batch.nonEmpty) {
            println("New batch: " + batch.dequeueAll(_ => true).mkString("[", ", ", "]"))
            println("Stream Finished.")
          }
        }
      })
    }
  }

  val batcherSink = Sink.fromGraph(new Batcher(10))

  randomGeneratorSource.to(batcherSink).run()
}

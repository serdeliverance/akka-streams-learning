package part3_graph

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}

/**
 * Bidirectional flows are useful when you need some kind of reversable operation.
 *
 * Ex: encrypt/decrypt
 */
object BidirectionalFlows extends App {

  implicit val system = ActorSystem("BidirectionalFlowDemo")
  implicit val materializer = ActorMaterializer()

  /*
    Example: cryptography
   */
  def encrypt(n: Int)(string: String) = string.map(c => (c + n).toChar)
  def decrypt(n: Int)(string: String) = string.map(c => (c - n).toChar)

  // bidirectional flow
  val bidiCryptoStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val encryptionFlowShape = builder.add(Flow[String].map(encrypt(3)))
    val decryptionFlowShape = builder.add(Flow[String].map(decrypt(3)))

    // BidiShape(encryptionFlowShape.in, encryptionFlowShape.out, decryptionFlowShape.in, decryptionFlowShape.out)
    BidiShape.fromFlows(encryptionFlowShape, decryptionFlowShape)
  }

  val unencryptedStrings = List("akka", "is", "awesome", "testing", "bidirectional", "flows")
  val unencryptedSource = Source(unencryptedStrings)
  val encryptedSource = Source(unencryptedStrings.map(encrypt(3)))

  val cryptoBidiGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val unencryptedSourceShape = builder.add(unencryptedSource)
      val encryptedSourceShape = builder.add(encryptedSource)
      val bidi = builder.add(bidiCryptoStaticGraph)

      val encryptedSinkShape = builder.add(Sink.foreach[String](string => println(s"encrypted: $string")))
      val decryptedSinkShape = builder.add(Sink.foreach[String](string => println(s"decrypted: $string")))

      unencryptedSourceShape ~> bidi.in1  ; bidi.out1 ~> encryptedSinkShape
      decryptedSinkShape     <~ bidi.out2 ; bidi.in2  <~ encryptedSourceShape

      ClosedShape
    }
  )

  cryptoBidiGraph.run()
}

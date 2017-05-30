package akka.stream.impl

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, FlowShape, _ }
import akka.stream.impl.StreamLayout.AtomicModule
import akka.stream.scaladsl.{ BidiFlow, Flow, Keep, Sink, Source }

class CompositeTestSource extends AtomicModule[SourceShape[Any], Any] {
  val out = Outlet[Any]("testSourceC.out")
  override val shape: Shape = SourceShape(out)
  override val traversalBuilder = TraversalBuilder.atomic(this, Attributes.name("testSource"))

  override def withAttributes(attributes: Attributes): AtomicModule[SourceShape[Any], Any] = ???
  override def toString = "TestSource"
}

class CompositeTestSink extends AtomicModule[SinkShape[Any], Any] {
  val in = Inlet[Any]("testSinkC.in")
  override val shape: Shape = SinkShape(in)
  override val traversalBuilder = TraversalBuilder.atomic(this, Attributes.name("testSink"))

  override def withAttributes(attributes: Attributes): AtomicModule[SinkShape[Any], Any] = ???
  override def toString = "TestSink"
}

class CompositeTestFlow(tag: String) extends AtomicModule[FlowShape[Any, Any], Any] {
  val in = Inlet[Any](s"testFlowC$tag.in")
  val out = Outlet[Any](s"testFlowC$tag.out")
  override val shape: Shape = FlowShape(in, out)
  override val traversalBuilder = TraversalBuilder.atomic(this, Attributes.name(s"testFlow$tag"))

  override def withAttributes(attributes: Attributes): AtomicModule[FlowShape[Any, Any], Any] = ???
  override def toString = s"TestFlow$tag"
}

object TraversalTest {
  def main(args: Array[String]) = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    try {
      //      val source = new CompositeTestSource
      //      val sink = new CompositeTestSink
      //      val atomicBuilder = sink.traversalBuilder
      //      val flow = new CompositeTestFlow("aaa")
      //
      //      println(source.traversalBuilder.isTraversalComplete)
      //      println(sink.traversalBuilder.isTraversalComplete)
      //
      //      val builder =
      //        source.traversalBuilder
      //          .add(sink.traversalBuilder, sink.shape, Keep.right)
      //          .wire(source.out, sink.in)
      //      println(source.traversalBuilder.isTraversalComplete)
      //      println(sink.traversalBuilder.isTraversalComplete)
      //      println(builder.isTraversalComplete)

      val sinkIgnore = Sink.ignore
      val sourceSingle = Source.single(1)
      val flowIdentity = Flow[Int]
      val sourceSingleViaIdentity = sourceSingle.via(flowIdentity)

      Flow.fromSinkAndSource[Any, Int](sinkIgnore, sourceSingleViaIdentity)

      Source(1 to 10).via(flowIdentity).via(Flow[Int].map(x ⇒ x * x)).via(Flow[Int].map(x ⇒ x * x)).via(flowIdentity).runForeach(x ⇒ println("waahhh:", x))

      val bidi1 = BidiFlow.fromFlows(Flow[Int].map(x ⇒ 2 * x), Flow[Int].map(x ⇒ 3 * x))
      val builder1 = bidi1.traversalBuilder
      bidi1.shape.inlets.foreach(in ⇒ println(s"bidi1: ${in}, id = ${in.id}, offset = ${builder1.offsetOf(in)}"))
      bidi1.shape.outlets.foreach(out ⇒ println(s"bidi1: ${out}, id = ${out.id}, offset = ${builder1.offsetOfModule(out)}"))

      val bidi2 = BidiFlow.fromFlows(Flow[Int].map(x ⇒ 2 * x), Flow[Int].map(x ⇒ 3 * x))
      val builder2 = bidi2.traversalBuilder
      bidi2.shape.inlets.foreach(in ⇒ println(s"bidi2: ${in}, id = ${in.id}, offset = ${builder2.offsetOf(in)}"))
      bidi2.shape.outlets.foreach(out ⇒ println(s"bidi2: ${out}, id = ${out.id}, offset = ${builder2.offsetOfModule(out)}"))

      val bidi3 = BidiFlow.fromFlows(Flow[Int].map(x ⇒ 2 * x), Flow[Int].map(x ⇒ 3 * x))
      val builder3 = bidi3.traversalBuilder
      bidi3.shape.inlets.foreach(in ⇒ println(s"bidi3: ${in}, id = ${in.id}, offset = ${builder3.offsetOf(in)}"))
      bidi3.shape.outlets.foreach(out ⇒ println(s"bidi3: ${out}, id = ${out.id}, offset = ${builder3.offsetOfModule(out)}"))

      val source = Source(1 to 10)
      println("-------------------------------------------------")
      source.shape.inlets.foreach(in ⇒ println(s"source: ${in}, id = ${in.id}, offset = ${source.traversalBuilder.offsetOf(in)}"))
      source.shape.outlets.foreach(out ⇒ println(s"source: ${out}, id = ${out.id}, offset = ${source.traversalBuilder.offsetOfModule(out)}"))

      val bidi123 = bidi1.atop(bidi2).atop(bidi3)
      println("-------------------------------------------------")
      val builder123 = bidi123.traversalBuilder
      bidi123.shape.inlets.foreach(in ⇒ println(s"bidi123: ${in}, id = ${in.id}, offset = ${builder123.offsetOf(in)}"))
      bidi123.shape.outlets.foreach(out ⇒ println(s"bidi123: ${out}, id = ${out.id}, offset = ${builder123.offsetOfModule(out)}"))

      println("finished")
      //      val flow2x = Flow[Int].map(x ⇒ x * 2)
      //      val flow16x = flow2x.via(flow2x).via(flow2x).via(flow2x)
      //      val sinkPrint = Sink.foreach((x: Int) ⇒ println(x))
      //      val sink16x = flow16x.to(Sink.ignore)
      //      val source1 = Source.single(1)

      //      source1.to(sink16x).run

    } finally {
      system.terminate()
    }
  }
}
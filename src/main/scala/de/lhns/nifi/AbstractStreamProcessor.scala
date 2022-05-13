package de.lhns.nifi

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, IOLocal}
import de.lhns.nifi.AbstractStreamProcessor.PROP_BATCH_SIZE
import fs2.{Pipe, Stream}
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor.util.StandardValidators

import scala.jdk.CollectionConverters._

abstract class AbstractStreamProcessor extends AbstractIOProcessor {
  override def supportedPropertyDescriptors: Seq[PropertyDescriptor] =
    Seq(PROP_BATCH_SIZE)

  private val localBatchSize: IOLocal[Option[Int]] = IOLocal[Option[Int]](None).unsafeRunSync()(IORuntime.global)

  final def batchSizeIO: IO[Int] = localBatchSize.get.map(_.getOrElse(throw new IllegalStateException()))

  override final def onTrigger: IO[Unit] =
    for {
      context <- contextIO
      session <- sessionIO
      batchSize = context.getProperty(PROP_BATCH_SIZE).evaluateAttributeExpressions.asInteger
      _ <- localBatchSize.set(Some(batchSize))
      _ <- Stream.iterable(session.get(batchSize).asScala)
        .through(stream)
        .compile
        .drain
    } yield ()

  def stream: Pipe[IO, FlowFile, Unit]
}

object AbstractStreamProcessor {
  val PROP_BATCH_SIZE: PropertyDescriptor = new PropertyDescriptor.Builder()
    .name("Batch Size")
    .required(true)
    .defaultValue("1")
    .addValidator(StandardValidators.INTEGER_VALIDATOR)
    .build
}

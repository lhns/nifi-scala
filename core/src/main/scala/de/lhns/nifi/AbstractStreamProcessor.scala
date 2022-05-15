package de.lhns.nifi

import cats.effect.IO
import de.lhns.nifi.AbstractStreamProcessor.PROP_BATCH_SIZE
import fs2.{Pipe, Stream}
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor.util.StandardValidators

import scala.jdk.CollectionConverters._

abstract class AbstractStreamProcessor extends AbstractIOProcessor {
  override def supportedPropertyDescriptors: Seq[PropertyDescriptor] =
    Seq(PROP_BATCH_SIZE)

  override final def onTrigger(implicit context: Context): IO[Unit] = {
    val batchSize = context.context.getProperty(PROP_BATCH_SIZE).evaluateAttributeExpressions.asInteger
    Stream.iterable(context.session.get(batchSize).asScala)
      .through(stream)
      .compile
      .drain
  }

  def stream(implicit context: Context): Pipe[IO, FlowFile, Unit]
}

object AbstractStreamProcessor {
  val PROP_BATCH_SIZE: PropertyDescriptor = new PropertyDescriptor.Builder()
    .name("Batch Size")
    .required(true)
    .defaultValue("1")
    .addValidator(StandardValidators.INTEGER_VALIDATOR)
    .build
}

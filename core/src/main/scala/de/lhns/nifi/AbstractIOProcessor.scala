package de.lhns.nifi

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.effect.unsafe.IORuntime
import fs2.Stream
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor._

import java.util
import java.util.function.Consumer
import scala.jdk.CollectionConverters._

abstract class AbstractIOProcessor extends AbstractSessionFactoryProcessor {
  protected val chunkSize = 10240

  final def exportTo(flowFile: FlowFile)(implicit context: Context): Stream[IO, Byte] =
    fs2.io.readOutputStream(chunkSize) { outputStream =>
      context.exportSemaphore.permit.use { _ =>
        IO.blocking {
          context.session.exportTo(flowFile, outputStream)
        }
      }
    }

  final def importFrom(stream: Stream[IO, Byte], flowFile: FlowFile)(implicit context: Context): IO[FlowFile] =
    fs2.io.toInputStreamResource(stream).use { inputStream =>
      IO.blocking {
        context.session.importFrom(inputStream, flowFile)
      }
    }

  override final lazy val getSupportedPropertyDescriptors: util.List[PropertyDescriptor] =
    util.Arrays.asList(supportedPropertyDescriptors: _*)

  override final lazy val getRelationships: util.Set[Relationship] = relationships.asJava

  override final def onTrigger(context: ProcessContext, sessionFactory: ProcessSessionFactory): Unit = {
    val session = sessionFactory.createSession()
    try {
      (for {
        exportSemaphore <- Semaphore[IO](1)
        _ <- onTrigger(new Context(
          context = context,
          session = session,
          exportSemaphore = exportSemaphore,
        ))
        _ <- IO.async_[Unit] { complete =>
          session.commitAsync(
            () => complete(Right(())),
            new Consumer[Throwable] {
              override def accept(t: Throwable): Unit = complete(Left(t))
            }
          )
        }
      } yield ())
        .unsafeRunSync()(IORuntime.global)
    } catch {
      case t: Throwable =>
        session.rollback(true)
        throw t
    }
  }

  def supportedPropertyDescriptors: Seq[PropertyDescriptor]

  def relationships: Set[Relationship]

  def onTrigger(implicit context: Context): IO[Unit]
}

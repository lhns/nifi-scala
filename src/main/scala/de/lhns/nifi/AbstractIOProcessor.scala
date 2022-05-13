package de.lhns.nifi

import cats.effect.std.Semaphore
import cats.effect.unsafe.IORuntime
import cats.effect.{IO, IOLocal}
import de.lhns.nifi.AbstractIOProcessor.Context
import fs2.Stream
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor._

import java.util
import java.util.function.Consumer
import scala.jdk.CollectionConverters._

abstract class AbstractIOProcessor extends AbstractSessionFactoryProcessor {
  protected val chunkSize = 10240

  private val localContext: IOLocal[Context] = IOLocal[Context](null).unsafeRunSync()(IORuntime.global)

  final def contextIO: IO[ProcessContext] = localContext.get.map(_.context)

  final def sessionIO: IO[ProcessSession] = localContext.get.map(_.session)

  final def exportTo(flowFile: FlowFile): Stream[IO, Byte] =
    Stream.eval(localContext.get).flatMap { c =>
      fs2.io.readOutputStream(chunkSize) { outputStream =>
        c.exportSemaphore.permit.use { _ =>
          IO.blocking {
            c.session.exportTo(flowFile, outputStream)
          }
        }
      }
    }

  final def importFrom(stream: Stream[IO, Byte], flowFile: FlowFile): IO[FlowFile] =
    localContext.get.flatMap { c =>
      fs2.io.toInputStreamResource(stream).use { inputStream =>
        IO.blocking {
          c.session.importFrom(inputStream, flowFile)
        }
      }
    }

  def supportedPropertyDescriptors: Seq[PropertyDescriptor]

  def relationships: Set[Relationship]

  def onTrigger: IO[Unit]

  override final lazy val getSupportedPropertyDescriptors: util.List[PropertyDescriptor] =
    util.Arrays.asList(supportedPropertyDescriptors: _*)

  override final lazy val getRelationships: util.Set[Relationship] = relationships.asJava

  override final def onTrigger(context: ProcessContext, sessionFactory: ProcessSessionFactory): Unit = {
    val session = sessionFactory.createSession()
    try {
      (for {
        exportSemaphore <- Semaphore[IO](1)
        _ <- localContext.set(Context(
          context = context,
          session = session,
          exportSemaphore = exportSemaphore,
        ))
        _ <- onTrigger
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
}

object AbstractIOProcessor {
  private case class Context(
                              context: ProcessContext,
                              session: ProcessSession,
                              exportSemaphore: Semaphore[IO],
                            )
}

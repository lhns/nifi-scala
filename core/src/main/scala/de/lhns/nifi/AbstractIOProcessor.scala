package de.lhns.nifi

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor._
import java.util
import java.util.function.Consumer
import scala.jdk.CollectionConverters._

abstract class AbstractIOProcessor extends AbstractSessionFactoryProcessor {
  override final lazy val getSupportedPropertyDescriptors: util.List[PropertyDescriptor] =
    util.Arrays.asList(supportedPropertyDescriptors: _*)

  override final lazy val getRelationships: util.Set[Relationship] = relationships.asJava

  override final def onTrigger(context: ProcessContext, sessionFactory: ProcessSessionFactory): Unit = {
    val session = sessionFactory.createSession()
    try {
      (for {
        c <- Context(
          context = context,
          session = session,
        )
        _ <- onTrigger(c)
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

  def onTrigger(implicit c: Context): IO[Unit]
}

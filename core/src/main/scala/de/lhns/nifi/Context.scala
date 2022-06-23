package de.lhns.nifi

import cats.effect.std.Semaphore
import cats.effect.IO
import fs2.Stream
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor._

class Context(
               val context: ProcessContext,
               val session: ProcessSession,
               private[nifi] val exportSemaphore: Semaphore[IO],
             ) {
  final def exportTo(flowFile: FlowFile, chunkSize: Int = 1024 * 64): Stream[IO, Byte] =
    fs2.io.readOutputStream(chunkSize) { outputStream =>
      exportSemaphore.permit.use { _ =>
        IO.blocking {
          session.exportTo(flowFile, outputStream)
        }
      }
    }

  final def importFrom(stream: Stream[IO, Byte], flowFile: FlowFile): IO[FlowFile] =
    fs2.io.toInputStreamResource(stream).use { inputStream =>
      IO.blocking {
        session.importFrom(inputStream, flowFile)
      }
    }
}

object Context {
  def apply(
             context: ProcessContext,
             session: ProcessSession,
           ): IO[Context] =
    for {
      exportSemaphore <- Semaphore[IO](1)
    } yield new Context(
      context,
      session,
      exportSemaphore,
    )
}

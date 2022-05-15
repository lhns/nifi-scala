package de.lhns.nifi

import cats.effect.IO
import cats.effect.std.Semaphore
import org.apache.nifi.processor.{ProcessContext, ProcessSession}

class Context(
               val context: ProcessContext,
               val session: ProcessSession,
               private[nifi] val exportSemaphore: Semaphore[IO],
             )

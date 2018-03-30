package com

import scala.annotation.tailrec
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

package object waz {
  case class Timeout(duration: FiniteDuration)

  implicit class FutureSyntax[A](val f: Future[A]) extends AnyVal {
    def await(implicit timeout: Timeout): A = Await.result(f, timeout.duration)
  }

  implicit class PromiseSyntax[A](val p: Promise[A]) extends AnyVal {
    def await(implicit timeout: Timeout): A = Await.result(p.future, timeout.duration)
  }

  implicit class DurationSyntax(val d: FiniteDuration) extends AnyVal {
    def idle(): Unit = Thread.sleep(d.toNanos / 1000000L, (d.toNanos % 1000000L).toInt)
  }

  def within(d: FiniteDuration)(assertion: => Unit): Unit = {
    val deadline = d.fromNow
    @tailrec def attempt(): Unit = Try(assertion) match {
      case Success(()) => ()
      case Failure(cause) =>
        if ((deadline + 10.millis).isOverdue) throw cause else {
          Thread.sleep(10)
          attempt()
        }
    }
    attempt()
  }
}

package net.mcarolan.hop

import scalaz.concurrent.Task

object Main extends App {

  import Hop._

  def printMessage(message: Message): Task[Action] =
    for {
      _ <- Task(println(message.payload))
    }
      yield Ack

  def processB(message: Message): Task[Action] =
    for {
      _ <- Task(println("Received from B " + message))
    }
      yield Nack

  println("Starting...")

  val rabbitClient = RabbitClient("localhost")

  val testPublisher = rabbitClient.publish(Exchange(""), RoutingKey("test"))_

  val consumers = Seq(Consumer(QueueName("a"), printMessage),
                      Consumer(QueueName("b"), processB))

  rabbitClient.compile(consumers:_*).run.run
}

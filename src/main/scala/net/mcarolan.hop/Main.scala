package net.mcarolan.hop

import scalaz.concurrent.Task

object Main extends App {

  import Hop._

  def printMessage(message: Message): Task[Action] =
    for {
      _ <- Task(println(message.payload))
    }
      yield Ack

  def processB(publisher: Message => Task[Unit], message: Message): Task[Action] =
    for {
      _ <- Task(println("Received from B " + message))
      _ <- publisher(Message(MessagePayload("Have you seen this? " + message.payload.value)))
    }
      yield Nack

  println("Starting...")

  val rabbitClient = RabbitClient("localhost")

  val testPublisher = rabbitClient.publish(Exchange(""), RoutingKey("a"))_

  val consumers = Seq(Consumer(QueueName("a"), printMessage),
                      Consumer(QueueName("b"), processB(testPublisher, _)))

  rabbitClient.compile(consumers:_*).run.run
}

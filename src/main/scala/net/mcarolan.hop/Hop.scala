package net.mcarolan.hop

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import scalaz._
import Scalaz._
import scalaz.concurrent.Task

object Hop {

	case class MessagePayload[T](payload: T)
	case class Message[T](payload: MessagePayload[T])

	case class QueueName(value: String)

	case class RabbitClient() {

		def publish(message: Message[String], queueName: QueueName): Task[Unit] =
			???

	}

	case class RabbitConnection(hostname: String, port: Int = 5672) {

		def apply[T](thunk: RabbitClient => T): T = {
			val connectionFactory = new ConnectionFactory()
			connectionFactory.setHost(hostname)
			connectionFactory.setPort(port)

			var connection: Option[Connection] = None
			try {
				connection = Some(connectionFactory.newConnection())
				thunk(RabbitClient())
			}
			finally {
				connection.foreach(_.close())
			}
		}

	}
}

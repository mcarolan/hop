package net.mcarolan.hop

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import scalaz._
import Scalaz._
import scalaz.concurrent.Task
import scalaz.stream._
import scalaz.stream.async.mutable.Queue

object Hop {

	case class MessagePayload(payload: String)
	case class Message(payload: MessagePayload)

	case class QueueName(value: String)

	def resource[T](ctor: => T, dtor: T => Unit): Process[Task, T] =
		Process.await(Task { ctor }) { value =>
			Process eval Task {
				value
			} onComplete { Process eval_ Task { dtor(value) } }
		}

	case class RabbitClient(connection: Connection) {

		private val channelStream = resource[Channel](connection.createChannel(), _.close())

		def publish(queueName: QueueName)(message: Message): Task[Unit] =
			(channelStream map { channel =>
				channel.basicPublish("", queueName.value, new BasicProperties(), message.payload.payload.getBytes)
				println("publish")
			}).run

	}

	case class RabbitConnection(hostname: String, port: Int = 5672) {

		private def connectionFactory = {
			val factory = new ConnectionFactory()
			factory.setHost(hostname)
			factory.setPort(port)
			factory
		}

		private val connectionStream = resource[Connection](connectionFactory.newConnection(), _.close())

		def apply(thunk: RabbitClient => Unit) {
			connectionStream.map(c => thunk(RabbitClient(c))).run.run
		}

	}
}

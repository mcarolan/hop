package net.mcarolan.hop

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import com.rabbitmq.client.QueueingConsumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.AMQP
import scalaz._
import Scalaz._
import scalaz.concurrent.Task
import scalaz.stream._
import scalaz.stream.async.mutable.Queue

object Hop {

	case class MessagePayload(value: String)
	case class Message(payload: MessagePayload)

	sealed trait Action
	case object Ack extends Action
	case object Nack extends Action

	case class RoutingKey(value: String)
	case class Exchange(value: String)
	case class QueueName(value: String)
	case class Consumer(queueName: QueueName, handler: Message => Task[Action])

	def resource[T](ctor: => T, dtor: T => Unit): Process[Task, T] =
		Process.await(Task { ctor }) { value =>
			Process eval Task {
				value
			} onComplete { Process eval_ Task { dtor(value) } }
		}

	case class RabbitClient(hostname: String, port: Int = 5672) {

		private def connectionFactory = {
			val factory = new ConnectionFactory()
			factory.setHost(hostname)
			factory.setPort(port)
			factory
		}

		private case class ToPublish(exchange: Exchange, routingKey: RoutingKey, message: Message)

		private val connectionStream = resource[Connection](connectionFactory.newConnection(), _.close())
		private def channelStream(conn: Connection): Process[Task, Channel] =
			resource[Channel](conn.createChannel(), _.close())

		private def doPublish(channel: Channel)(toPublish: ToPublish): Task[Unit] = Task {
			channel.basicPublish(toPublish.exchange.value, toPublish.routingKey.value, new BasicProperties(), toPublish.message.payload.value.getBytes)
		}

		private def publishSink(conn: Connection): Sink[Task, ToPublish] =
			channelStream(conn) flatMap { chan =>
				Process repeatEval Task(doPublish(chan)_)
			}

		private val publishQ: async.mutable.Queue[ToPublish] = async.boundedQueue(10)
		private def publisherStream(conn: Connection): Process[Task, Unit] = (publishQ.dequeue to publishSink(conn))

		private case class RawMessage(envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte])

		private def consumerProcess(connection: Connection, consumer: Consumer): Process[Task, Unit] =
			channelStream(connection) flatMap { chan =>
				def createConsumer: Task[QueueingConsumer] = Task {
					val queueingConsumer = new QueueingConsumer(chan)
					chan.basicConsume(consumer.queueName.value, false, queueingConsumer)
					queueingConsumer
				}

				def readMessage(queueingConsumer: QueueingConsumer): Task[RawMessage] = Task {
					val delivery = queueingConsumer.nextDelivery()
					RawMessage(delivery.getEnvelope(), delivery.getProperties(), delivery.getBody())
				}

				def processMessage(message: RawMessage): Task[Unit] = {
				 Task(Message(MessagePayload(new String(message.body)))) flatMap consumer.handler flatMap {
						case Ack => Task { chan.basicAck(message.envelope.getDeliveryTag(), false) }
						case Nack => Task { chan.basicNack(message.envelope.getDeliveryTag(), false, false) }
					}
				}

				val source: Process[Task, RawMessage] = (Process eval createConsumer) flatMap (queueingConsumer => Process repeatEval readMessage(queueingConsumer))
				val sink: Sink[Task, RawMessage] = Process repeatEval Task(processMessage _)

				(source to sink)
			}

		def publish(exchange: Exchange, routingKey: RoutingKey)(message: Message): Task[Unit] =
			publishQ.enqueueOne(ToPublish(exchange, routingKey, message))

		def compile(consumer: Consumer*): Process[Task, Unit] =
			connectionStream flatMap { conn =>
				val consumers = consumer.map(consumerProcess(conn, _))
				val publisher = publisherStream(conn)
				val processes = NonEmptyList(publisher, consumers:_*)
				processes.foldLeft(Process.empty[Task, Unit])(_ merge _)
			}

	}
}

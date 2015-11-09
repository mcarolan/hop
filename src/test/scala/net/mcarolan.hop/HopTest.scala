package net.mcarolan.hop

import org.scalatest._
import org.http4s.Http4s._

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel

class HopTest extends FunSuite with ManagementApiMatchers {

	import Hop._

	val managementApi = ManagementApi(uri("http://localhost:8080"))

	test("RabbitConnection should open and close the connection to rabbit") {
		//start off with no connections
		managementApi.closeAllConnections().flatMap(_ => shouldHaveNoConnections).run

		RabbitConnection("localhost") { client =>
			shouldHaveAConnection.run
		}

		//end with no connections
		shouldHaveNoConnections.run
	}

	test("RabbitConnection should close the connection to rabbit if thunk throws") {
		//start off with no connections
		managementApi.closeAllConnections().flatMap(_ => shouldHaveNoConnections).run

		intercept[RuntimeException] { RabbitConnection("localhost") { client =>
				throw new RuntimeException("blah")
			}
		}

		//end with no connections
		shouldHaveNoConnections.run
	}

	// test("Should be able to publish to a queue") {
	// 	//start off with no connections
	// 	val preconditions =
	// 		for {
	// 			_ <- managementApi.closeAllConnections()
	// 			_ <- shouldHaveNoConnections
	// 			_ <- managementApi.declareQueue("basicPublishTest")
	// 			_ <- managementApi.ensureEmpty("basicPublishTest")
	// 			_ <- shouldHaveNoMessages("basicPublishTest")
	// 		}
	// 			yield ()
	//
	// 	preconditions.run
	//
	// 	RabbitConnection("localhost") { client =>
	// 		client.publish(Message(MessagePayload("blah")), QueueName("basicPublishTest")).run
	// 		shouldHaveOneMessage("basicPublishTest").run
	// 	}
	//
	// 	//end with no connections
	// 	shouldHaveNoConnections.run
	// }

}

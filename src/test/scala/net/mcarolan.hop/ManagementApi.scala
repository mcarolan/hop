package net.mcarolan.hop

import argonaut._
import Argonaut._
import org.http4s.Http4s.{uri => _, _}
import org.http4s._
import org.http4s.headers._
import scalaz.concurrent.Task
import org.http4s.argonaut.jsonOf
import org.scalatest.Matchers
import org.http4s.dsl._
import scalaz._
import Scalaz._
import scalaz.stream.Process
import scodec.bits.ByteVector

case class ManagementApi(base: Uri) {
  val client = org.http4s.client.blaze.defaultClient

	case class Connection(name: String)

  case class QueueGetRequest(count: Int, requeue: Boolean = true, encoding: String = "auto")

  object QueueGetRequest {
    implicit def QueueGetRequestEncodeJson: EncodeJson[QueueGetRequest] =
      EncodeJson(r => ("count", jNumber(r.count)) ->:
                      ("requeue", jBool(r.requeue)) ->:
                      ("encoding", jString(r.encoding)) ->:
                      jEmptyObject)
  }

	object Connection {
		implicit def ConnectionDeocdeJson: DecodeJson[Connection] =
			DecodeJson(c => for {
				name <- (c --\ "name").as[String]
			} yield Connection(name))
	}

  case class ManagementApiMessage(payload: String)

  object ManagementApiMessage {
    implicit def MessageDecodeJson: DecodeJson[ManagementApiMessage] =
      DecodeJson(c => for {
        payload <- (c --\ "payload").as[String]
      } yield ManagementApiMessage(payload))
  }

  case class QueueStats(messages: Int)

  object QueueStats {
    implicit def QueueStatsDecodeJson: DecodeJson[QueueStats] =
      DecodeJson(c => for {
        messages <- (c --\ "messages").as[Int]
      } yield QueueStats(messages))
  }

  private def buildRequest(requestPath: /, requestMethod: Method = Method.GET, sendAcceptHeader: Boolean = true, requestBody: EntityBody = EmptyBody): Request = {
    //no accept header for delete
    val acceptHeader = sendAcceptHeader.option(Accept(NonEmptyList(MediaType.`application/json`)))
    val requestHeaders = List(Some(Authorization(BasicCredentials("guest", "guest"))),
                          Some(`Content-Type`(MediaType.`application/json`)),
                          acceptHeader)

    Request(uri=Uri.resolve(base, Uri(path = requestPath.asString)),
      headers=Headers(requestHeaders.flatten), method=requestMethod,
      body=requestBody)
  }

  def listConnections(): Task[List[Connection]] = {
    implicit val connectionsDecoder = jsonOf[List[Connection]]
    client(buildRequest(Root / "api" / "connections")) flatMap {
        case response if response.status.isSuccess => {
          response.as[List[Connection]]
        }
    }
  }

  def queueStats(queueName: String): Task[QueueStats] = {
    implicit val queueStatsDecoder = jsonOf[QueueStats]
    client(buildRequest(Root / "api" / "queues" / "/" / queueName)) flatMap {
      case response if response.status.isSuccess => {
        response.as[QueueStats]
      }
    }
  }

  def getMessages(queueName: String, count: Int): Task[List[ManagementApiMessage]] = {
    implicit val messageDecoder = jsonOf[List[ManagementApiMessage]]
    val body: EntityBody = Process(ByteVector(QueueGetRequest(count).asJson.nospaces.getBytes()))
    client(buildRequest(Root / "api" / "queues" / "/" / queueName / "get", requestMethod = Method.POST, sendAcceptHeader = false, requestBody = body)) flatMap {
      case response if response.status.isSuccess => {
        response.as[List[ManagementApiMessage]]
      }
    }
  }

  def declareQueue(name: String): Task[Unit] =
    client(buildRequest(Root / "api" / "queues" / "/" / name, requestMethod = Method.PUT)).flatMap(failOnHttpError)

  def ensureEmpty(queueName: String): Task[Unit] =
    client(buildRequest(Root / "api" / "queues" / "/" / queueName / "contents", requestMethod = Method.DELETE, sendAcceptHeader = false)).flatMap(failOnHttpError)

  def closeConnection(connection: Connection): Task[Unit] =
    client(buildRequest(Root / "api" / "connections" / connection.name, requestMethod = Method.DELETE, sendAcceptHeader = false)).flatMap(failOnHttpError)

  def closeAllConnections(): Task[Unit] =
    for {
      connections <- listConnections()
      _ <- Task.gatherUnordered(connections.map(closeConnection))
    }
      yield ()

  private def failOnHttpError(response: Response): Task[Unit] =
    if (response.status.isSuccess) {
      Task.now(())
    }
    else {
      Task.fail(new IllegalStateException(response.status.toString))
    }
}

trait ManagementApiMatchers extends Matchers {

  def managementApi: ManagementApi

  def shouldHaveNoConnections: Task[Unit] =
    managementApi.listConnections().map(_ shouldBe 'empty)

  def shouldHaveAConnection: Task[Unit] =
    managementApi.listConnections().map(_ should have length 1)

  def shouldHaveOneMessage(queueName: String): Task[Unit] =
    managementApi.queueStats(queueName).map(_.messages should be(1))

  def shouldHaveNoMessages(queueName: String): Task[Unit] =
    managementApi.queueStats(queueName).map(_.messages should be(0))
}

//
// Copyright 2012 Vibul Imtarnasan and other Plebify contributors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package org.mashupbots.plebify.http

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.StringRequestEntity
import org.mashupbots.plebify.core.Engine
import org.mashupbots.plebify.core.StartRequest
import org.mashupbots.plebify.core.StartResponse
import org.scalatest.GivenWhenThen
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.slf4j.LoggerFactory
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import akka.camel.CamelMessage
import akka.camel.Consumer
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import org.apache.camel.Exchange

object HttpConnectorSpec {

  val onCreatedSaveConfig = """
	on-receive-send-request {
      connectors = [{
          connector-id = "http"
          factory-class-name = "org.mashupbots.plebify.http.HttpConnectorFactory"
        }]
      jobs = [{
          job-id = "job1"
          on = [{
              connector-id = "http"
              connector-event = "request-received"
              uri = "http://localhost:8877/on-receive-send-request"
	        }]
          do = [{
              connector-id = "http"
              connector-task = "send-request"
              uri = "http://localhost:9977/dummy-listender"
              method = "POST"
              retry-interval = 1
	        }]
        }]
	}
    
	akka {
	  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
	  loglevel = "DEBUG"
	}    
    """

  lazy val cfg = List(onCreatedSaveConfig).mkString("\n")

  /**
   * Send a GET request
   *
   * @param url HTTP Connection
   * @returns (status, content)
   */
  def httpGet(url: String): (Int, String) = {
    val client = new HttpClient()
    val method: GetMethod = new GetMethod(url)
    try {
      val statusCode: Int = client.executeMethod(method)
      val responseBytes: Array[Byte] = method.getResponseBody(100000)
      val responseContent: String = if (responseBytes == null || responseBytes.length == 0) ""
      else new String(responseBytes)

      (statusCode, responseContent)
    } catch {
      case e: Throwable => (500, e.toString)
    } finally {
      method.releaseConnection()
    }
  }

  /**
   * Send a GET request
   *
   * @param url HTTP Connection
   * @param content Content to post
   * @param contentType Content type
   * @returns (status, content)
   */
  def httpPost(url: String, content: String, contentType: String): (Int, String) = {
    val client = new HttpClient()
    val method: PostMethod = new PostMethod(url)
    method.setRequestEntity(new StringRequestEntity(content, contentType, "UTF-8"))
    method.setRequestHeader("Content-type", contentType);
    try {
      val statusCode: Int = client.executeMethod(method)
      val responseBytes: Array[Byte] = method.getResponseBody(100000)
      val responseContent: String = if (responseBytes == null || responseBytes.length == 0) ""
      else new String(responseBytes)

      (statusCode, responseContent)
    } catch {
      case e: Throwable => (500, e.toString)
    } finally {
      method.releaseConnection()
    }
  }

}

class HttpConnectorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpec
  with MustMatchers with GivenWhenThen {

  val log = LoggerFactory.getLogger("HttpConnectorSpec")
  def this() = this(ActorSystem("HttpConnectorSpec", ConfigFactory.parseString(HttpConnectorSpec.cfg)))

  "Http Connector" must {

    "be able to receive and send an http request" in {
      val engine = system.actorOf(Props(new Engine(configName = "on-receive-send-request")), "on-receive-send-request")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          m.isSuccess must be(true)
        }
      }

      val dummyListener = system.actorOf(Props[DummyHttpListener], "DummyHttpListener")
      Thread.sleep(2000)

      info("Send GET and POST")
      val (responseCode1, content1) =
        HttpConnectorSpec.httpPost("http://localhost:8877/on-receive-send-request", "hello", "text/plain")
      responseCode1 must be(200)

      val (responseCode2, content2) =
        HttpConnectorSpec.httpGet("http://localhost:8877/on-receive-send-request")
      responseCode2 must be(200)

      Thread.sleep(500)

      dummyListener ! "dump"
      expectMsgPF(1 seconds) {
        case m: List[_] => {
          m.size must be(2)

          val m1 = m(0).asInstanceOf[Map[String, Any]]
          log.info("M1: {}", m1)
          m1("Body") must be("hello")
          m1("Content-Length") must be("5")
          m1("Content-Type") must be("text/plain")

          val m2 = m(1).asInstanceOf[Map[String, Any]]
          log.info("M2: {}", m2)
          m2("Content-Length") must be("0")          
        }
      }

      info("Endpoint has errors, there should be 3 retries 1 second apart")
      val (responseCode5, content5) =
        HttpConnectorSpec.httpPost("http://localhost:8877/on-receive-send-request", "return error", "text/plain")
      responseCode5 must be(200)
      
      Thread.sleep(10000)

      dummyListener ! "dump"
      expectMsgPF(1 seconds) {
        case m: List[_] => {
          log.info("Messages received by our DummyHttpListener {}", m)
          m.size must be(6)	// 2 from previous test, and 4 from this test (1 initial and 3 resends)
        }
      }
    }
        
  }
}

class DummyHttpListener extends Consumer {
  def endpointUri = "jetty:http://localhost:9977/dummy-listender"

  val messages = ListBuffer[Map[String, Any]]()
  def receive = {
    case msg: CamelMessage =>
      val body = if (msg.body != null) msg.bodyAs[String] else ""
      
        // msg.body is a stream so we don't want to send it back in the "dump".
      // we covert the stream to a string
      val data = msg.headers.toMap ++ Map(("Body", body))
      messages += data

      if (body == "return error")
        sender ! CamelMessage("", Map((Exchange.HTTP_RESPONSE_CODE, "500")))
      else
        sender ! "Hello"

    case "dump" =>
      sender ! messages.toList
  }
}



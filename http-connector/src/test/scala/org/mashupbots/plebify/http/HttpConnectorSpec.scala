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

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import scala.concurrent.duration.DurationInt
import org.mashupbots.plebify.core.Engine
import org.mashupbots.plebify.core.StartRequest
import org.mashupbots.plebify.core.StartResponse
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.slf4j.LoggerFactory
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.StringRequestEntity
import akka.camel.Consumer
import akka.camel.CamelMessage
import scala.collection.mutable.ListBuffer
import akka.camel.CamelExtension
import org.mashupbots.plebify.core.EventData
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
  with MustMatchers with BeforeAndAfterAll {

  val log = LoggerFactory.getLogger("HttpConnectorSpec")
  def this() = this(ActorSystem("HttpConnectorSpec", ConfigFactory.parseString(HttpConnectorSpec.cfg)))

  "Http Connector processing" must {

    "send and receive http requests" in {
      // Start
      val engine = system.actorOf(Props(new Engine(configName = "on-receive-send-request")), "on-receive-send-request")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          m.isSuccess must be(true)
        }
      }

      // Start dummy http end point to receive data from SendRequestTask
      val dummyListener = system.actorOf(Props[DummyHttpListener], "DummyHttpListener")

      Thread.sleep(2000)

      // Do it
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

          val camel = CamelExtension(system)
          val camelContext = camel.context

          val m1 = m(0).asInstanceOf[CamelMessage]
          log.info("{}", m1)
          m1.getBodyAs(classOf[String], camelContext) must be("hello")
          m1.headers("Content-Length") must be("5")
          m1.headers("Content-Type") must be("text/plain")
          
          val m2 = m(1).asInstanceOf[CamelMessage]
          log.info("{}", m2)
          m2.headers("Content-Length") must be("0")

        }
      }
    }

  }
}

class DummyHttpListener extends Consumer {
  def endpointUri = "jetty:http://localhost:9977/dummy-listender"

  val messages = ListBuffer[CamelMessage]()
  def receive = {
    case msg: CamelMessage =>
      messages += msg
      sender ! "Hello"

    case "dump" =>
      sender ! messages.toList
  }
}



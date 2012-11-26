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

import org.mashupbots.plebify.core.EventData
import org.mashupbots.plebify.core.TaskExecutionRequest
import org.mashupbots.plebify.core.config.TaskExecutionConfig
import akka.camel.CamelMessage
import akka.camel.Oneway
import akka.camel.Producer
import org.apache.camel.Exchange
import akka.camel.CamelMessage
import scala.concurrent.Await
import scala.concurrent.duration._
/**
 * Sends a HTTP request to the specified end point
 *
 * ==Parameters==
 *  - '''uri''': See [[http://camel.apache.org/jetty.html Apache Camel jetty component]] for options.
 *  - '''method''': GET, POST or PUT. Defaults to POST.
 *  - '''template''': Optional template for the post/put data. If not specified, value of `Contents` will be posted.
 *
 * ==Event Data==
 *  - '''Content''': Content to send. Ignored if method is GET.
 *
 * @param config Task configuration
 */
class SendRequestTask(config: TaskExecutionConfig) extends Producer with akka.actor.ActorLogging {

  def endpointUri = config.params("uri")
  require(endpointUri.startsWith("jetty:"), s"$endpointUri must start with 'jetty:'")

  val template = config.params.get("template")

  override def postStop() {
    log.info("Stopping")
  }

  //  override def postRestart(reason: Throwable) {
  //    log.info("PostRestart")
  //    implicit val ec = context.dispatcher
  //    Await.result(camel.deactivationFutureFor(self)(5 seconds, ec), 5 seconds)
  //    super.postRestart(reason)
  //  }

  override def preStart() {
    super.preStart()
    log.info("Starting")
  }

  /**
   * Transforms TaskExecutionRequest into a CamelMessage
   */
  override def transformOutgoingMessage(msg: Any) = msg match {
    case msg: TaskExecutionRequest => {
      val method = config.params.getOrElse("method", "POST")
      val content = if (method == "GET") ""
    	  		    else if (template.isDefined) EventData.mergeTemplate(template.get, msg.eventNotification.data)
    		        else msg.eventNotification.data.getOrElse(EventData.Content, "")
      
      val header = Map(
        (Exchange.HTTP_METHOD, method),
        (Exchange.CONTENT_TYPE, msg.eventNotification.data(EventData.ContentType)),
        (Exchange.CONTENT_ENCODING, "UTF-8"))

      CamelMessage(content, header)
    }
    case m => log.debug("Unexpected message {}", m)
  }
}
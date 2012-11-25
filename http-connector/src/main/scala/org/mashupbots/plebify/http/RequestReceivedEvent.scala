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

import java.util.Date
import scala.collection.immutable.List.apply
import org.mashupbots.plebify.core.EventData
import org.mashupbots.plebify.core.EventNotification
import org.mashupbots.plebify.core.EventSubscriptionRequest
import akka.camel.CamelMessage
import akka.camel.Consumer
import org.apache.camel.Exchange

/**
 * Processes a HTTP request when it is received
 *
 * Starts and HTTP endpoint and waits for incoming requests.
 *
 * If the request is successfully processed, a 200 HTTP response code will be returned. If not, a 500 response
 * code will be returned with the content containing the error message.
 *
 * ==Parameters==
 *  - '''uri''': See [[http://camel.apache.org/jetty.html Apache Camel jetty component]] for options.
 *  - '''contains''': Optional comma separated list of words or phrases to match before the event fires. For example,
 *    `error, warn` to match files containing the word `error` or `warn`.
 *  - '''matches''': Optional regular expression to match before the event fires. For example:
 *    `"^([\\s\\d\\w]*(ERROR|WARN)[\\s\\d\\w]*)$"` to match files containing the words `ERROR` or `WARN`.
 *
 * ==Event Data==
 *  - '''Date''': Timestamp when event occurred
 *  - '''Content''': Contents of the file
 *  - '''ContentLength''': Length of the content
 *  - '''ContentType''': MIME type
 *  - '''HttpUri''': URI of incoming request
 *  - '''HttpMethod''': HTTP method
 *  - '''HttpPath''': Path part of the URI
 *  - '''HttpQuery''': Query part of URI
 *  - '''HttpField_*''': HTTP headers and posted form data fields. For example `User-Agent` will be stored as
 *    `HttpField_User-Agent`.
 *
 * @param request Subscription request
 */
class RequestReceivedEvent(request: EventSubscriptionRequest) extends Consumer with akka.actor.ActorLogging {

  def endpointUri = request.config.params("uri")
  require(endpointUri.startsWith("jetty:"), s"$endpointUri must start with 'jetty:'")

  val contains: Option[List[String]] = {
    val c = request.config.params.get("contains")
    if (c.isEmpty) None
    else if (c.get.length == 0) None
    else Some(c.get.split(",").toList.filter(!_.isEmpty))
  }

  val matches: Option[String] = request.config.params.get("matches")

  def receive = {
    case msg: CamelMessage =>
      try {
        log.debug("HttpRequestReceivedEvent: {}", msg)

        val content = if (msg.body == null) "" else msg.bodyAs[String]
        val fireEvent = {
          if (contains.isDefined) contains.get.foldLeft(false)((result, word) => result || content.contains(word))
          else if (matches.isDefined) content.matches(matches.get)
          else true
        }

        if (fireEvent) {
          val httpFields: Map[String, String] = msg.headers
            .filter {
              case (key, value) => !key.startsWith("Camel") && value != null &&
                !RequestReceivedEvent.headersToIgnore.contains(key)
            }
            .map { case (key, value) => ("HttpField_" + key, value.toString) }

          val coreFields: Map[String, String] = Map(
            (EventData.Id, EventData.readCamelHeader(msg, Exchange.BREADCRUMB_ID)),
            (EventData.Date, EventData.dateTimeToString(new Date())),
            (EventData.Content, content),
            (EventData.ContentLength, EventData.readCamelHeader(msg, "Content-Length")),
            (EventData.ContentType, EventData.readCamelHeader(msg, "Content-Type")),
            ("HttpUri", EventData.readCamelHeader(msg, Exchange.HTTP_URI)),
            ("HttpMethod", EventData.readCamelHeader(msg, Exchange.HTTP_METHOD)),
            ("HttpPath", EventData.readCamelHeader(msg, Exchange.HTTP_PATH)),
            ("HttpQuery", EventData.readCamelHeader(msg, Exchange.HTTP_QUERY)))

          val data = coreFields ++ httpFields
          request.job ! EventNotification(request.config, data)

          sender ! CamelMessage("", Map((Exchange.HTTP_RESPONSE_CODE, "200")))
        } else {
          log.debug("Ignoring HTTP request '{}' because it does not fit contains or matches criteria", content)
        }
      } catch {
        case ex: Throwable =>
          log.error(ex, "Error processing {}", msg)
          sender ! CamelMessage(ex.toString, Map((Exchange.HTTP_RESPONSE_CODE, "500")))
      }
  }
}

/**
 * Companion object for [[org.mashupbots.plebify.http.RequestReceivedEvent]]
 */
object RequestReceivedEvent {
  private val headersToIgnore = List("MessageExchangeId", Exchange.BREADCRUMB_ID)
}
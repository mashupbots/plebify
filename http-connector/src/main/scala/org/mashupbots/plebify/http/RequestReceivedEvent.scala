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

import org.apache.camel.Exchange
import org.mashupbots.plebify.core.EventData
import org.mashupbots.plebify.core.EventNotification
import org.mashupbots.plebify.core.EventSubscriptionConfigReader
import org.mashupbots.plebify.core.EventSubscriptionRequest
import org.mashupbots.plebify.core.config.ConnectorConfig

import akka.camel.CamelMessage
import akka.camel.Consumer

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
 *  - '''ContentType''': MIME type
 *  - '''HttpUri''': URI of incoming request
 *  - '''HttpMethod''': HTTP method
 *  - '''HttpPath''': Path part of the URI
 *  - '''HttpQuery''': Query part of URI
 *  - '''HttpField_*''': HTTP headers and posted form data fields. For example `User-Agent` will be stored as
 *    `HttpField_User-Agent`.
 *
 * @param connectorConfig Connector configuration.
 * @param request Subscription request
 */
class RequestReceivedEvent(val connectorConfig: ConnectorConfig, val request: EventSubscriptionRequest) extends Consumer
  with EventSubscriptionConfigReader with akka.actor.ActorLogging {

  def endpointUri = configValueFor("uri")
  require(endpointUri.startsWith("jetty:"), s"$endpointUri must start with 'jetty:'")

  val contains: Option[List[String]] = {
    val c = configValueFor("contains", "")
    if (c.isEmpty) None
    else Some(c.split(",").toList.filter(!_.isEmpty))
  }

  val matches: String = configValueFor("matches", "")

  def receive = {
    case msg: CamelMessage =>
      try {
        log.debug("HttpRequestReceivedEvent: {}", msg)

        val content = if (msg.body == null) "" else msg.bodyAs[String]
        val fireEvent = {
          if (contains.isDefined) contains.get.foldLeft(false)((result, word) => result || content.contains(word))
          else if (!matches.isEmpty) content.matches(matches)
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
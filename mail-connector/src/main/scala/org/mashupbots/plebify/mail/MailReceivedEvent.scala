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
package org.mashupbots.plebify.mail

import java.util.Date
import org.mashupbots.plebify.core.EventData
import org.mashupbots.plebify.core.EventNotification
import org.mashupbots.plebify.core.EventSubscriptionRequest
import akka.camel.CamelMessage
import akka.camel.Consumer
import org.apache.camel.Exchange
import scala.util.matching.Regex

/**
 * New email is received
 *
 * Attachments are not currently supported.
 *
 * ==Parameters==
 *  - '''uri''': See [[http://camel.apache.org/mail.html Apache Camel mail component]] for options.
 *  - '''contains''': Optional comma separated list of words or phrases to match before the event fires. For example,
 *    `error, warn` to match files containing the word `error` or `warn`.
 *  - '''matches''': Optional regular expression to match before the event fires. For example:
 *    `"^([\\s\\d\\w]*(ERROR|WARN)[\\s\\d\\w]*)$"` to match files containing the words `ERROR` or `WARN`.
 *
 * ==Event Data==
 *  - '''Date''': Timestamp when event occurred
 *  - '''Content''': Contents of the email
 *  - '''ContentType''': MIME Type set to `text/plain` by default
 *  - '''SendOn''': Date the email was sent
 *  - '''From''': Sender's email address
 *  - '''To''': Receiver's email address
 *  - '''Subject''': Subject of the email
 *
 * @param request Subscription request
 */
class MailReceivedEvent(request: EventSubscriptionRequest) extends Consumer with akka.actor.ActorLogging {

  def endpointUri = request.config.params("uri")
  require(endpointUri.startsWith("imap") || endpointUri.startsWith("pop"), "imap or pop3 needed to receive email")

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
        log.debug("MailReceivedEvent: {}", msg)

        val content = if (msg.body == null) "" else msg.bodyAs[String]
        val fireEvent = {
          if (contains.isDefined) contains.get.foldLeft(false)((result, word) => result || content.contains(word))
          else if (matches.isDefined) content.matches(matches.get)
          else true
        }

        if (fireEvent) {
          val httpFields: Map[String, String] = msg.headers
            .filter { case (key, value) => !key.startsWith("Camel") && value != null }
            .map { case (key, value) => ("MailField_" + key, value.toString) }

          val data = Map(
            (EventData.Id, EventData.readCamelHeader(msg, Exchange.BREADCRUMB_ID)),
            (EventData.Date, EventData.dateTimeToString(new Date())),
            (EventData.Content, content),
            (EventData.ContentType, "text/plain"),
            ("SendOn", EventData.readCamelHeader(msg, "Date")),
            ("From", EventData.readCamelHeader(msg, "From")),
            ("To", EventData.readCamelHeader(msg, "To")),
            ("Subject", EventData.readCamelHeader(msg, "Subject")))

          request.job ! EventNotification(request.config, data)
        } else {
          log.debug("Ignoring {} because it does not fit contains or matches criteria", content)
        }
      } catch {
        case ex: Throwable =>
          log.error(ex, "Error processing {}", msg)
      }
  }
}
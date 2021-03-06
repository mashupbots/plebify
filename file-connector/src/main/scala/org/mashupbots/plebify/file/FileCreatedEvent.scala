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
package org.mashupbots.plebify.file

import java.util.Date

import org.apache.camel.Exchange
import org.mashupbots.plebify.core.EventData
import org.mashupbots.plebify.core.EventNotification
import org.mashupbots.plebify.core.EventSubscriptionConfigReader
import org.mashupbots.plebify.core.EventSubscriptionRequest
import org.mashupbots.plebify.core.config.ConnectorConfig

import akka.actor.Status.Failure
import akka.camel.Ack
import akka.camel.CamelMessage
import akka.camel.Consumer

/**
 * File created event.
 *
 * Looks for a file or files in a directory as specified by the `uri` configuration parameter.
 * When one is found, an event is triggered.
 *
 * ==Parameters==
 *  - '''uri''': Refer to [[http://camel.apache.org/file2.html Apache Camel file component]] common and consumer
 *    options.
 *  - '''mime-type''': Optional mime type. If not specified, one will be extrapolated using the file name extension.
 *  - '''contains''': Optional comma separated list of words or phrases to match before the event fires. For example,
 *    `error, warn` to match files containing the word `error` or `warn`.
 *  - '''matches''': Optional regular expression to match before the event fires. For example:
 *    `"^([\\s\\d\\w]*(ERROR|WARN)[\\s\\d\\w]*)$"` to match files containing the words `ERROR` or `WARN`.
 *
 * ==Event Data Outputs==
 *  - '''Date''': Timestamp when event occurred
 *  - '''Content''': Contents of the file
 *  - '''ContentLength''': Length of the file
 *  - '''ContentType''': MIME Type
 *  - '''LastModified''': When the file was last changed
 *  - '''FileName''': Name of file without path
 *
 * @param connectorConfig Connector configuration.
 * @param request Subscription request
 */
class FileCreatedEvent(val connectorConfig: ConnectorConfig, val request: EventSubscriptionRequest) extends Consumer
  with EventSubscriptionConfigReader with akka.actor.ActorLogging {

  // 
  // Have to not autoAck otherwise we get IOExcetion: Stream closed when reading files > 64K
  //
  // This is because stream caching is on and the temp file camel creates is deleted too soon if autoAck is turned on
  // https://groups.google.com/forum/?fromgroups=#!msg/akka-dev/HLtR_HmMM-Y/hfP5fFzXa_UJ
  //
  // An alternative is to turn streamingCache off in our 
  // akka {
  //  camel {
  //     streamingCache = off
  //   }
  // }
  //
  override def autoAck = false

  def endpointUri = configValueFor("uri")

  val defaultMimeType = configValueFor("mime-type", "text/plain")

  val contains: Option[List[String]] = {
    val c = configValueFor("contains", "")
    if (c.isEmpty) None
    else Some(c.split(",").toList.filter(!_.isEmpty))
  }

  val matches: String = configValueFor("matches", "")

  def receive = {
    case msg: CamelMessage =>
      try {
        log.debug("FileCreatedEvent: {}", msg)

        val fileName = EventData.readCamelHeader(msg, "CamelFileNameOnly")
        val content = if (msg.body == null) "" else msg.bodyAs[String]
        val fireEvent = {
          if (contains.isDefined) contains.get.foldLeft(false)((result, word) => result || content.contains(word))
          else if (!matches.isEmpty) content.matches(matches)
          else true
        }

        if (fireEvent) {
          val mimeType = if (defaultMimeType.isEmpty) EventData.fileNameToMimeType(fileName) else defaultMimeType
          val data = Map(
            (EventData.Id, EventData.readCamelHeader(msg, Exchange.BREADCRUMB_ID)),
            (EventData.Date, EventData.dateTimeToString(new Date())),
            (EventData.Content, content),
            (EventData.ContentType, mimeType),
            (EventData.LastModified, EventData.readCamelHeader(msg, "CamelFileLastModified")),
            ("FileName", fileName))

          request.job ! EventNotification(request.config, data)
        } else {
          log.debug("Ignoring {} because it does not fit contains or matches criteria", fileName)
        }
        sender ! Ack
      } catch {
        case ex: Throwable =>
          log.error(ex, "Error processing {}", msg)
          sender ! Failure(ex)
      }
  }
}
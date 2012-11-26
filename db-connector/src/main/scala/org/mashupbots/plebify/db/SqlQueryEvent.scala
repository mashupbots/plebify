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
package org.mashupbots.plebify.db

import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import scala.collection.JavaConversions._
import scala.concurrent.duration.DurationInt
import org.apache.camel.Exchange
import org.mashupbots.plebify.core.EventData
import org.mashupbots.plebify.core.EventNotification
import org.mashupbots.plebify.core.EventSubscriptionRequest
import akka.actor.Actor
import akka.actor.FSM
import akka.actor.PoisonPill
import akka.actor.Props
import akka.camel.CamelExtension
import akka.camel.CamelMessage
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout.durationToTimeout
import org.mashupbots.plebify.core.config.ConnectorConfig
import org.mashupbots.plebify.core.EventSubscriptionConfigReader

/**
 * FSM states for [[org.mashupbots.plebify.db.SqlQueryEvent]]
 */
sealed trait SqlQueryEventState

/**
 * FSM data for [[org.mashupbots.plebify.db.SqlQueryEvent]]
 */
trait SqlQueryEventData

/**
 * Runs an SQL query and fire when matching records are found
 *
 * Offloads the actual running of the SQL to [[org.mashupbots.plebify.db.SqlQueryEventWorker]] because camel JDBC only
 * supports producers.
 *
 * This actor is responsible for managing polling and filtering.
 *
 * ==Parameters==
 *  - '''datasource''': Name of datasource as specified in the connector config
 *  - '''sql''': SQL statement to execute
 *  - '''max-rows''': Optional maximum number of rows to be returned in a query. Defaults to `100` if not supplied.
 *  - '''initial-delay''': Optional number of seconds before polling is started. Defaults to `60` seconds.
 *  - '''interval''': Optional number of seconds between polling for the database. Defaults to `300` seconds.
 *  - '''sql-timeout''': Optional number of seconds to wait for query to return. Defaults to `10` seconds.
 *
 * ==Event Data==
 *  - '''Date''': Timestamp when event occurred
 *  - '''Content''': Contents of the email
 *
 * @param connectorConfig Connector configuration.
 * @param request Subscription request
 */
class SqlQueryEvent(val connectorConfig: ConnectorConfig, val request: EventSubscriptionRequest) extends Actor
  with FSM[SqlQueryEventState, SqlQueryEventData] with EventSubscriptionConfigReader with akka.actor.ActorLogging {

  import context.dispatcher

  val camel = CamelExtension(context.system)
  implicit val camelContext = camel.context

  val queryMessage = CamelMessage(configValueFor("sql"), Map.empty)

  //*******************************************************************************************************************
  // State
  //*******************************************************************************************************************
  /**
   * Not waiting for SQL request
   */
  case object Idle extends SqlQueryEventState

  /**
   * Sent query and waiting for a result
   */
  case object Active extends SqlQueryEventState

  //*******************************************************************************************************************
  // Data
  //*******************************************************************************************************************
  /**
   * No data
   */
  case object NoData extends SqlQueryEventData

  //*******************************************************************************************************************
  // Transitions
  //*******************************************************************************************************************
  startWith(Idle, NoData)

  val sql = configValueFor("sql")
  val initialDelay = configValueFor("initial-delay", "100").toInt
  val interval = configValueFor("interval", "300").toInt
  val sqlTimeout = configValueFor("sql-timeout", "10").toInt

  when(Idle) {
    case Event("tick", _) =>
      log.debug(s"Running sql for '${request.config.jobId}-${request.config.index}'")
      val worker = context.actorFor("worker")
      val future = ask(worker, queryMessage)(sqlTimeout seconds).mapTo[CamelMessage]
      future pipeTo self
      goto(Active)
    case unknown =>
      log.debug("Recieved message while Uninitialised: {}", unknown.toString)
      stay
  }

  when(Active) {
    case Event(msg: CamelMessage, _) =>
      log.debug("SQL Result for {}-{}: {}", request.config.jobId, request.config.index, msg)

      val rowCount = msg.headers("CamelJdbcRowCount").asInstanceOf[Int]
      if (rowCount > 0) {
        val javaRows = msg.bodyAs[ArrayList[HashMap[String, Object]]]
        val rows = javaRows.toList
        val sqlResult = (for {
          i <- 1 to rows.size
          row = rows(i - 1)
          column <- row.keys
        } yield (s"row$i-$column", row(column).toString))

        val data: Map[String, String] = Map(
          (EventData.Id, EventData.readCamelHeader(msg, Exchange.BREADCRUMB_ID)),
          (EventData.Date, EventData.dateTimeToString(new Date())),
          (EventData.Content, sqlResult.mkString("\n")),
          (EventData.ContentType, "text/plain")) ++ sqlResult.toMap

        request.job ! EventNotification(request.config, data)
      }

      goto(Idle)
    case Event(msg: akka.actor.Status.Failure, _) => {
      log.error(msg.cause, s"Error running sql for '${request.config.jobId}-${request.config.index}'")
      goto(Idle)
    }
    case unknown =>
      log.debug("Recieved unknown message while Active: {}", unknown.toString)
      stay
  }

  onTermination {
    case StopEvent(FSM.Normal, state, data) =>
      log.debug("SqlQueryEvent stopped")
    case StopEvent(FSM.Failure(cause: Throwable), state, data) =>
      log.error(cause, s"SqlQueryEvent failed with error: ${cause.getMessage}")
    case _ =>
      log.debug(s"SqlQueryEvent shutdown")
  }

  override def preStart() {
    val worker = context.actorOf(Props(new SqlQueryEventWorker(request)), "worker")
    context.system.scheduler.schedule(initialDelay seconds, interval seconds, self, "tick")
  }

  override def postStop() {
    context.actorFor("worker") ! PoisonPill
  }

}
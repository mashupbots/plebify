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
package org.mashupbots.plebify.core

import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration._

import org.mashupbots.plebify.core.config.ConnectorConfig
import org.mashupbots.plebify.core.config.JobConfig

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.FSM
import akka.actor.Props
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout.durationToTimeout

/**
 * FSM states for [[org.mashupbots.plebify.core.Job]]
 */
sealed trait JobState

/**
 * FSM data for [[org.mashupbots.plebify.core.Job]]
 */
trait JobData

/**
 * A job waits for connector events to fire and executing tasks.
 *
 * When you instance a job, it starts.  When you send a `PoisonPill` or `Kill` message, it stops.
 *
 * Upon starting, a job will subscribe to events by sending a [[org.mashupbots.plebify.core.EventSubsription]] to the
 * specified connector.
 *
 * When the event fires, the job will receive a [[org.mashupbots.plebify.core.EventNotification]] message.  This will
 * trigger the job to instance a [[org.mashupbots.plebify.core.JobWorker]] to execute the specified task(s).
 *
 * @param jobConfig Configuration for the job represented by this Actor
 */
class Job(jobConfig: JobConfig) extends Actor with FSM[JobState, JobData] with akka.actor.ActorLogging {

  import context.dispatcher

  private case class Subscribe() extends NotificationMessage

  //*******************************************************************************************************************
  // State
  //*******************************************************************************************************************
  /**
   * Job has just started but is yet to initialize connectors and jobs
   */
  case object Uninitialized extends JobState

  /**
   * Job is in the process of subscribing to events
   */
  case object Initializing extends JobState

  /**
   * Job is initialized and ready to receive events and execute tasks
   */
  case object Initialized extends JobState

  //*******************************************************************************************************************
  // Data
  //*******************************************************************************************************************
  /**
   * No data present
   */
  case object NoData extends JobData

  /**
   * Data used during initialization process
   */
  case class InitializationData(starter: ActorRef) extends JobData

  //*******************************************************************************************************************
  // Transitions
  //*******************************************************************************************************************
  startWith(Uninitialized, NoData)

  when(Uninitialized) {
    case Event(request: StartRequest, _) =>
      log.info(s"Starting job '${jobConfig.id}'")
      self ! Subscribe()
      goto(Initializing) using InitializationData(sender)
    case unknown =>
      log.debug("Recieved message while Uninitialised: {}", unknown.toString)
      if (sender != self) sender ! Uninitilized()
      stay
  }

  when(Initializing) {
    case Event(msg: Subscribe, data: InitializationData) =>
      // Subscribe and send Future[Seq[StartResponse]] message back to ourself
      subscribe()
    case Event(msg: Seq[_], data: InitializationData) =>
      // Future successful. Check for errors in all StartResponse received
      val errors = filterErrors(msg)
      if (errors.size > 0) {
        stop(FSM.Failure(new Error(s"Error subscribing to one or more events.")))
      } else {
        data.starter ! StartResponse()
        goto(Initialized)
      }
    case Event(msg: akka.actor.Status.Failure, data: InitializationData) =>
      stop(FSM.Failure(new Error(s"Error while waiting for event subscription futures. ${msg.cause.getMessage}",
        msg.cause)))
    case unknown =>
      log.debug("Recieved unknown message while Initializing: {}", unknown.toString)
      if (sender != self) sender ! Uninitilized()
      stay
  }

  when(Initialized) {
    case Event(_, _) => stay
  }

  onTermination {
    case StopEvent(FSM.Failure(cause: Throwable), state, data: InitializationData) =>
      data.starter ! StartResponse(Some(new Error(s"Error starting job. ${cause.getMessage}", cause)))
    case _ =>
      log.info(s"Job shutdown")

  }

  private def filterErrors(msg: Seq[_]): Seq[StartResponse] = {
    val responses = msg.asInstanceOf[Seq[StartResponse]]
    responses.filter(r => !r.isSuccess)
  }

  private def subscribe(): State = {
    try {
      val futures = Future.sequence(jobConfig.events.map(eventConfig => {
        log.debug(s"Job ${jobConfig.id} subscribing to ${eventConfig.id}")
        val connectorActorName = ConnectorConfig.createActorName(eventConfig.connectorId)
        val connector = context.system.actorFor(s"$connectorActorName")
        ask(connector, EventSubscriptionRequest(eventConfig))(2 seconds).mapTo[StartResponse]
      }))

      // Send Future[Seq[StartResponse]] message to ourself when future finishes
      futures pipeTo self
      stay
    } catch {
      case e: Throwable => stop(FSM.Failure(new Error(s"Error subscribing to event. ${e.getMessage}", e)))
    }
  }

  private def unsubscribe() = {
    jobConfig.events.foreach(eventConfig => {
      val msg = s"Job ${jobConfig.id} unsubscribing from ${eventConfig.id}"
      log.debug(msg)
      try {
        val connectorActorName = ConnectorConfig.createActorName(eventConfig.connectorId)
        val connector = context.system.actorFor(s"$connectorActorName")
        if (!connector.isTerminated) {
          connector ! EventSubscriptionRequest(eventConfig)
        }
      } catch {
        case e: Throwable => log.error(s"Ignoring error while $msg. ${e.getMessage}.", e)
      }
    })
  }

  //*******************************************************************************************************************
  // Boot up
  //*******************************************************************************************************************
  log.debug(s"Job ${jobConfig.id} Actor '${context.self.path.toString}'")
  initialize

}


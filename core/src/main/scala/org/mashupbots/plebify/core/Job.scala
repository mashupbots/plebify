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

import org.mashupbots.plebify.core.config.ConnectorConfig
import org.mashupbots.plebify.core.config.JobConfig

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.FSM
import akka.actor.Props

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
class Job(val jobConfig: JobConfig) extends Actor with FSM[JobState, JobData] with akka.actor.ActorLogging {

  //*******************************************************************************************************************
  // State
  //*******************************************************************************************************************
  /**
   * Job has stopped
   */
  case object Stopped extends JobState

  /**
   * Job is stopping
   */
  case object Stopping extends JobState

  /**
   * Job is starting
   */
  case object Starting extends JobState

  /**
   * Job has started
   */
  case object Started extends JobState

  //*******************************************************************************************************************
  // Data
  //*******************************************************************************************************************
  /**
   * No data present
   */
  case object NoData extends JobData

  /**
   * Details the event that is being subscribed/unsubscribed
   *
   *  @param idx Index of the current job being started or stopped
   */
  case class EventProgress(idx: Int, sender: ActorRef) extends JobData {

    /**
     * True if there are more events to process
     */
    val hasNext: Boolean = (idx + 1) < jobConfig.events.size

    /**
     * Current event
     */
    val currentEvent = jobConfig.events(idx)

    /**
     * Move to next event
     */
    lazy val next = if (hasNext) null else this.copy(idx = idx + 1)
    
    /**
     * Description of the current event for error message
     */
    val description = s"event ${currentEvent.id} in job ${jobConfig.id}"
  }

  //*******************************************************************************************************************
  // Transitions
  //*******************************************************************************************************************
  startWith(Stopped, NoData)

  when(Stopped) {
    case Event(request: StartRequest, NoData) => {
      val progress = EventProgress(0, sender)
      try {
        log.info(s"Starting job ${jobConfig.id}")
        goto(Starting) using subscribe(progress)
      } catch {
        case e: Throwable =>
          sender ! StartResponse(s"Error subscribing to ${progress.description}", Some(e))
          goto(Stopped) using NoData
      }
    }
  }

  when(Starting) {
    // Get a response from a connector
    case Event(response: EventSubscriptionResponse, progress: EventProgress) => {
      try {
        if (response.isSuccess) {
          if (progress.hasNext) {
            stay using subscribe(progress.next)
          } else {
            goto(Started) using NoData
          }
        } else {
          throw new Error(response.error.get.getMessage, response.error.get)
        }
      } catch {
        case e: Throwable =>
          progress.sender ! StartResponse(s"Error subscribing to ${progress.description}", response.error)
          goto(Stopped) using NoData
      }
    }
  }

  when(Started) {
    case Event(request: StopRequest, NoData) => {
      log.info(s"Stopping job ${jobConfig.id}")
      val progress = EventProgress(0, sender)
      goto(Stopping) using unsubscribe(progress)
    }

    case Event(notification: EventNotification, NoData) => {
      val jobWorker = context.system.actorOf(Props[JobWorker], name = s"plebify-jobworker-${jobConfig.id}-" +
        UUID.randomUUID())
      stay
    }
  }

  when(Stopping) {
    // Got a response from a job
    case Event(response: EventUnsubscriptionResponse, progress: EventProgress) => {
      if (!response.isSuccess) {
        log.error(response.error.get, s"Error unsubscribing from event ${progress.currentEvent.id}")
      }
      if (progress.hasNext) {
        stay using unsubscribe(progress.next)
      } else {
        goto(Stopped) using NoData
      }
    }
  }

  onTermination {
    case StopEvent(FSM.Normal, state, data) =>
      log.info(s"Job '${jobConfig.id}' stopped.")

    case StopEvent(FSM.Shutdown, state, data) =>
      log.info(s"Shutting down job '${jobConfig.id}'")

    case StopEvent(FSM.Failure(cause: String), state, progress) =>
      log.error(s"Error in job ${jobConfig.id}. " + cause)
  }

  private def subscribe(progress: EventProgress): EventProgress = {
    callConnector(progress, EventSubscriptionRequest(progress.currentEvent))
  }

  private def unsubscribe(progress: EventProgress): EventProgress = {
    callConnector(progress, EventUnsubscriptionRequest(progress.currentEvent))
  }

  private def callConnector(progress: EventProgress, msg: ConnectorMessage): EventProgress = {
    val eventConfig = progress.currentEvent
    val connectorActorName = ConnectorConfig.createActorName(eventConfig.connectorId)
    val connector = context.system.actorFor(s"$connectorActorName")
    if (connector.isTerminated) {
      throw new Error(s"Connector $connectorActorName is not running!")
    }
    connector ! msg
    progress
  }

  //*******************************************************************************************************************
  // Boot up
  //*******************************************************************************************************************
  log.debug(s"Job ${jobConfig.id} Actor '${context.self.path.toString}'")
  initialize

}


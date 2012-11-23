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

import scala.Some.apply
import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.concurrent.duration._

import org.mashupbots.plebify.core.config.ConnectorConfig
import org.mashupbots.plebify.core.config.JobConfig
import org.mashupbots.plebify.core.config.MaxWorkerStrategy

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.FSM
import akka.actor.Props
import akka.actor.Terminated
import akka.pattern.ask
import akka.pattern.pipe

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
 * ==Starting==
 * Jobs are started by [[org.mashupbots.plebify.core.Engine]].
 *
 * Startup follows the [[org.mashupbots.plebify.core.StartRequest]]/[[org.mashupbots.plebify.core.StartResponse]]
 * message pattern.
 *
 * Upon starting, a job will subscribe to events by sending a [[org.mashupbots.plebify.core.EventSubsription]] to the
 * specified connector.  The job is deemed initialized only when it has successfully subscribed to all its
 * connector events.
 *
 * ==Stopping==
 * Jobs are stopped by [[org.mashupbots.plebify.core.Engine]] using the standard methods.
 *
 * ==Processing==
 * When the event fires, the connector event will send a [[org.mashupbots.plebify.core.EventNotification]] message to
 * the job. The job reacts to this message by instancing a [[org.mashupbots.plebify.core.JobWorker]] to asynchronously
 * execute the specified task(s).
 *
 * This master/worker pattern allows more than one event notification to be processed concurrently. The number of
 * concurrent workers by settings in [[org.mashupbots.plebify.core.config.JobConfig]].
 *  - `workerConcurrencyStrategy`
 *  - `workerConcurrencyCount`
 *
 * If `workerConcurrencyStrategy` is `router`, [[org.mashupbots.plebify.core.JobWorker]] is instanced
 * `workerConcurrencyCount` times and used behind an Akka router.
 *
 * If `workerConcurrencyStrategy` is `ceiling`, [[org.mashupbots.plebify.core.EventNotification]]s will be ignored
 * once the number of [[org.mashupbots.plebify.core.JobWorker]] exceeds `workerConcurrencyCount`.  This is the default
 * setting with a `workerConcurrencyCount` of 5. This means that if there are 5
 * [[org.mashupbots.plebify.core.JobWorker]] actors running, any new trigger events will be ignored until one of
 * the [[org.mashupbots.plebify.core.JobWorker]] terminates and finishes processing.
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
   * Job is initialized and ready to process event notifications
   */
  case object Active extends JobState

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

  /**
   * Data used during the [[org.mashupbots.plebify.core.Job.Active]] state
   *
   * @param workerCount Number of [[org.mashupbots.plebify.core.JobWorker]]s started and active.
   * @param queue Queue for storing event notifications when `workerCount` exceeds the maximum.
   */
  case class ActiveData(workerCount: Int, queue: Queue[EventNotification]) extends JobData {

    def incrementWokerCount(): ActiveData = {
      this.copy(workerCount = workerCount + 1)
    }

    def decrementWokerCount(): ActiveData = {
      if (workerCount > 0) this.copy(workerCount = workerCount - 1)
      else this.copy(workerCount = 0)
    }

    def enqueue(msg: EventNotification): ActiveData = {
      this.copy(queue = queue.enqueue(msg))
    }

  }

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
      // Future successful. Check for errors in all EventSubscriptionResponse received
      val errors = filterErrors(msg)
      if (errors.size > 0) {
        stop(FSM.Failure(new Error(s"Error subscribing to one or more events.")))
      } else {
        data.starter ! StartResponse()
        goto(Active) using ActiveData(0, Queue[EventNotification]())
      }
    case Event(msg: akka.actor.Status.Failure, data: InitializationData) =>
      stop(FSM.Failure(new Error(s"Error while waiting for event subscription futures. ${msg.cause.getMessage}",
        msg.cause)))
    case unknown =>
      log.debug("Recieved unknown message while Initializing: {}", unknown.toString)
      if (sender != self) sender ! Uninitilized()
      stay
  }

  when(Active) {
    case Event(msg: EventNotification, data: ActiveData) =>
      if (data.workerCount < jobConfig.maxWorkerCount) {
        // We have not exceeded the max, so start JobWorker and increment the count
        startWorker(msg)
        stay using data.incrementWokerCount()
      } else {
        // We have exceeded the max, so queue or reschedule
        jobConfig.maxWorkerStrategy match {
          case MaxWorkerStrategy.Queue => {
            if (data.queue.size < jobConfig.queueSize) {
              log.info("Queueing {} because all workers are busy.", msg.toString)
              stay using data.enqueue(msg)
            } else {
              log.info("Ignoring {} because max queue size of {} has been reached.", msg.toString, jobConfig.queueSize)
              stay
            }
          }
          case MaxWorkerStrategy.Reschedule => {
            log.info("Rescheduling {} because all workers are busy.", msg.toString)
            context.system.scheduler.scheduleOnce(jobConfig.rescheduleInterval seconds, self, msg)
            stay
          }
        }
      }

    case Event(msg: Terminated, data: ActiveData) =>
      // A JobWorker has terminated so reduce the count if queue is empty
      // If not empty, start a new JobWorker to process queue item
      if (data.queue.isEmpty) {
        stay using data.decrementWokerCount()
      } else {
        val (msg, newQueue) = data.queue.dequeue
        startWorker(msg)
        stay using data.copy(queue = newQueue)
      }
  }

  onTermination {
    case StopEvent(FSM.Failure(cause: Throwable), state, data: InitializationData) =>
      data.starter ! StartResponse(Some(new Error(s"Error starting job. ${cause.getMessage}", cause)))
      log.error(cause, s"Job terminating with error: ${cause.getMessage}")
    case _ =>
      log.info(s"Job shutdown")
  }

  private def filterErrors(msg: Seq[_]): Seq[EventSubscriptionResponse] = {
    val responses = msg.asInstanceOf[Seq[EventSubscriptionResponse]]
    responses.filter(r => !r.isSuccess)
  }

  private def subscribe(): State = {
    try {
      val futures = Future.sequence(jobConfig.events.map(eventConfig => {
        log.debug(s"Subscribing to ${eventConfig.connectorId}-${eventConfig.connectorEvent} for ${eventConfig.name}")
        val connectorActorName = ConnectorConfig.createActorName(eventConfig.connectorId)
        val connector = context.actorFor(s"../$connectorActorName")
        val msg = EventSubscriptionRequest(eventConfig, self)
        ask(connector, msg)(eventConfig.initializationTimeout seconds).mapTo[EventSubscriptionResponse]
      }))

      // Send Future[Seq[StartResponse]] message to ourself when future finishes
      futures pipeTo self
      stay
    } catch {
      case e: Throwable => stop(FSM.Failure(new Error(s"Error subscribing to event. ${e.getMessage}", e)))
    }
  }

  private def unsubscribe() {
    jobConfig.events.foreach(eventConfig => {
      val msg = s"Unsubscribing to ${eventConfig.connectorId}-${eventConfig.connectorEvent} for ${eventConfig.name}"
      log.debug(msg)
      try {
        val connectorActorName = ConnectorConfig.createActorName(eventConfig.connectorId)
        val connector = context.system.actorFor(s"$connectorActorName")
        if (!connector.isTerminated) {
          connector ! EventUnsubscriptionRequest(eventConfig, self)
        }
      } catch {
        case e: Throwable => log.error(s"Ignoring error while $msg. ${e.getMessage}.", e)
      }
    })
  }

  private def startWorker(msg: EventNotification) {
    val worker = context.actorOf(Props(new JobWorker(jobConfig, msg)), "jobworker-" + UUID.randomUUID().toString)

    // Register death watch so that we will receive a Terminate message when the actor stops
    context.watch(worker)
  }

  //*******************************************************************************************************************
  // Boot up
  //*******************************************************************************************************************
  log.debug(s"Job ${jobConfig.id} Actor '${context.self.path.toString}'")
  initialize

}


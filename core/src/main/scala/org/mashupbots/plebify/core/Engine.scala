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

import scala.concurrent.Future
import scala.concurrent.duration._

import org.mashupbots.plebify.core.config.PlebifyConfig

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.FSM
import akka.actor.Props
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout.durationToTimeout

/**
 * FSM states for [[org.mashupbots.plebify.core.Engine]]
 */
sealed trait EngineState

/**
 * FSM data for [[org.mashupbots.plebify.core.Engine]]
 */
trait EngineData

/**
 * The Plebify engine manages connectors and jobs.
 *
 * @param configName Name of root Plebify element parsed in the AKKA config. Defaults to `plebify`.
 */
class Engine(val configName: String = "plebify") extends Actor
  with FSM[EngineState, EngineData] with akka.actor.ActorLogging {

  import context.dispatcher

  private val config: PlebifyConfig = new PlebifyConfig(context.system.settings.config, configName)

  private case class InitializeConnectors() extends NotificationMessage

  private case class InitializeJob() extends NotificationMessage

  //*******************************************************************************************************************
  // State
  //*******************************************************************************************************************
  /**
   * Engine has just started but is yet to initialize connectors and jobs
   */
  case object Uninitialized extends EngineState

  /**
   * Waiting for connectors to start
   */
  case object InitializingConnectors extends EngineState

  /**
   * Waiting for jobs to start
   */
  case object InitializingJobs extends EngineState

  /**
   * Engine is initialized and ready
   */
  case object Initialized extends EngineState

  /**
   * Error occurred and actor is in the process of shutting down
   */
  case object Error extends EngineState

  //*******************************************************************************************************************
  // Data
  //*******************************************************************************************************************
  /**
   * No data present
   */
  case object NoData extends EngineData

  /**
   * Data used during initialization process
   */
  case class InitializationData(starter: ActorRef) extends EngineData
  
  //*******************************************************************************************************************
  // Transitions
  //*******************************************************************************************************************
  startWith(Uninitialized, NoData)

  when(Uninitialized) {
    case Event(request: StartRequest, _) =>
      log.info("Starting Plebify")
      self ! InitializeConnectors()
      goto(InitializingConnectors) using InitializationData(sender)
    case unknown =>
      log.debug("Recieved message while Uninitialized: {}", unknown.toString)
      if (sender != self) sender ! Uninitilized()
      stay
  }

  when(InitializingConnectors) {
    case Event(msg: InitializeConnectors, data: InitializationData) =>
      initializeConnectors()
    case Event(msg: Seq[_], data: InitializationData) =>
      val errors = filterErrors(msg)
      if (errors.size > 0) {
        stop(FSM.Failure(new Error("Error starting one or more connectors.")))
      } else {
        self ! InitializeJob()
        goto(InitializingJobs)
      }
    case Event(msg: akka.actor.Status.Failure, data: InitializationData) =>
      stop(FSM.Failure(new Error("Error while waiting for connector start futures. ${msg.cause.getMessage}", msg.cause)))
    case unknown =>
      log.debug("Recieved unknown message while InitializingConnectors: {}", unknown.toString)
      if (sender != self) sender ! Uninitilized()
      stay
  }

  when(InitializingJobs) {
    case Event(msg: InitializeJob, data: InitializationData) =>
      initializeJobs()
    case Event(msg: Seq[_], data: InitializationData) =>
      val errors = filterErrors(msg)
      if (errors.size > 0) {
        stop(FSM.Failure(new Error("Error starting one or more jobs.")))
      } else {
        data.starter ! StartResponse()
        goto(Initialized)
      }
    case Event(msg: akka.actor.Status.Failure, data: InitializationData) =>
      stop(FSM.Failure(new Error("Error while waiting for job start futures. ${msg.cause.getMessage}", msg.cause)))
    case unknown =>
      log.debug("Recieved unknown message while InitializingJobs: {}", unknown.toString)
      if (sender != self) sender ! Uninitilized()
      stay
  }

  when(Initialized) {
    case Event(_, _) => stay
  }

  onTermination {
    case StopEvent(FSM.Failure(cause: Throwable), state, data: InitializationData) =>
      data.starter ! StartResponse(Some(new Error(s"Error starting Plebify. ${cause.getMessage}", cause)))
    case _ =>
      log.info(s"Plebify shutdown")
  }

  private def initializeConnectors(): State = {
    try {
      val futures = Future.sequence(config.connectors.map(connectorConfig => {
        log.info(s"Starting connector ${connectorConfig.id}")
        val clazz = Class.forName(connectorConfig.factoryClassName)
        val factory = clazz.newInstance().asInstanceOf[ConnectorFactory]
        val connector = factory.create(context, connectorConfig)
        ask(connector, StartRequest())(5 seconds).mapTo[StartResponse]
      }))

      // Send Future[Seq[StartResponse]] message to ourself when future finishes
      futures pipeTo self
      stay
    } catch {
      case e: Throwable => stop(FSM.Failure(new Error(s"Error starting connector. ${e.getMessage}", e)))
    }
  }

  private def initializeJobs(): State = {
    try {
      val futures = Future.sequence(config.jobs.map(jobConfig => {
        val job = context.actorOf(Props(new Job(jobConfig)), name = jobConfig.actorName)
        ask(job, StartRequest())(5 seconds).mapTo[StartResponse]
      }))

      // Send Future[Seq[StartResponse]] message to ourself when future finishes
      futures pipeTo self
      stay
    } catch {
      case e: Throwable => stop(FSM.Failure(new Error(s"Error starting job. ${e.getMessage}", e)))
    }
  }

  private def filterErrors(msg: Seq[_]): Seq[StartResponse] = {
    val responses = msg.asInstanceOf[Seq[StartResponse]]
    responses.filter(r => !r.isSuccess)
  }

  //*******************************************************************************************************************
  // Boot up
  //*******************************************************************************************************************
  initialize
}


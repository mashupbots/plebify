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
 * The plebify engine manages connectors and jobs for you.
 *
 * The engine uses immutable configuration. If you wish to change any configuration, you will have to stop and
 * restart the engine.
 *
 * == Starting ==
 * To start [[org.mashupbots.plebify.core.Engine]], create it as an actor and send it a
 * [[org.mashupbots.plebify.core.StartRequest]] message. A [[org.mashupbots.plebify.core.StartResponse]] message will
 * be returned when the actor is fully initialized and ready to handle incoming messages.
 *
 * {{{
 * // Create instance
 * val engine = system.actorOf(Props[Engine], name = "plebify")
 *
 * // Start
 * engine ! StartRequest()
 *
 * // Alternatively, you can start and wait for response
 * val future: Future[StartResponse] = ask(engine, msg).mapTo[StartResponse]
 * }}}
 *
 * During the initialization process, connectors and jobs are started as child actors so that they can be supervised
 * and monitored.
 *
 * == Stopping ==
 * You can stop the [[org.mashupbots.plebify.core.Engine]] using normal actor termination methods:
 *
 * {{{
 * // Stop
 * engine.stop()
 *
 * // Poison Pill
 * engine ! PoisonPill()
 *
 * // Graceful Stop
 * try {
 *   val stopped: Future[Boolean] = gracefulStop(engine, 5 seconds)(system)
 *   Await.result(stopped, 6 seconds)
 * } catch {
 *  case e: akka.pattern.AskTimeoutException => log.error("Timeout waiting for plebify to stop")
 * }
 * }}}
 *
 * == Configuration ==
 * It is assumed that configuration is loaded into the Akka actor system. See
 * [[http://doc.akka.io/docs/akka/2.1.0-RC2/general/configuration.html Akka configuration]] documentation
 * for more details.
 *
 * The root element name defaults to `plebify`. You can override this in the constructor.
 *
 * See [[org.mashupbots.plebify.core.config.PlebifyConfig]] for configuration specifics.
 *
 * @param configName Name of root plebify element parsed in the AKKA config. Defaults to `plebify`.
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
   * Engine is initialized and ready to process messages
   */
  case object Active extends EngineState

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
      log.debug("Received message while Uninitialized: {}", unknown.toString)
      if (sender != self) sender ! Uninitilized()
      stay
  }

  when(InitializingConnectors) {
    case Event(msg: InitializeConnectors, data: InitializationData) =>
      initializeConnectors()
    case Event(msg: Seq[_], data: InitializationData) =>
      val errors = filterErrors(msg)
      if (errors.size > 0) {
        errors.foreach(r => log.error(r.error.get, "Error starting engine."))
        stop(FSM.Failure(new Error("Error starting one or more connectors. Check logs for details.")))
      } else {
        self ! InitializeJob()
        goto(InitializingJobs)
      }
    case Event(msg: akka.actor.Status.Failure, data: InitializationData) =>
      stop(FSM.Failure(new Error(s"Error while waiting for connector start futures. ${msg.cause.getMessage}", msg.cause)))
    case unknown =>
      log.debug("Received unknown message while InitializingConnectors: {}", unknown.toString)
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
        goto(Active)
      }
    case Event(msg: akka.actor.Status.Failure, data: InitializationData) =>
      stop(FSM.Failure(new Error(s"Error while waiting for job start futures. ${msg.cause.getMessage}", msg.cause)))
    case unknown =>
      log.debug("Received unknown message while InitializingJobs: {}", unknown.toString)
      if (sender != self) sender ! Uninitilized()
      stay
  }

  when(Active) {
    case Event(_, _) =>
      // For the time being, the engine does not support any messages
      // In the future, we add support for reporting on the status of connectors and jobs as well as performance
      // statistics.
      stay
  }

  onTermination {
    case StopEvent(FSM.Failure(cause: Throwable), state, data: InitializationData) =>
      data.starter ! StartResponse(Some(new Error(s"Error starting Plebify. ${cause.getMessage}", cause)))
      log.error(cause, s"Plebify terminating with error: ${cause.getMessage}")
    case _ =>
      log.info("Plebify shutdown")
  }

  private def initializeConnectors(): State = {
    try {
      val futures = Future.sequence(config.connectors.map(connectorConfig => {
        log.info(s"Starting connector ${connectorConfig.id}")
        val clazz = Class.forName(connectorConfig.factoryClassName)
        val factory = clazz.newInstance().asInstanceOf[ConnectorFactory]
        val connector = factory.create(context, connectorConfig)
        ask(connector, StartRequest())(connectorConfig.initializationTimeout seconds).mapTo[StartResponse]
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
        ask(job, StartRequest())(jobConfig.initializationTimeout seconds).mapTo[StartResponse]
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


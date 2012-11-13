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

import org.mashupbots.plebify.core.config.ConnectorConfig
import org.mashupbots.plebify.core.config.PlebifyConfig

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.FSM
import akka.actor.Props

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
 * To start, send a [[org.mashupbots.plebify.core.StartRequest]] message.
 *
 * To stop, send a [[org.mashupbots.plebify.core.StopRequst]] message.
 *
 * @param configName Name of root Plebify element parsed in the AKKA config. Defaults to `plebify`.
 */
class Engine(val configName: String = "plebify") extends Actor
  with FSM[EngineState, EngineData] with akka.actor.ActorLogging {

  /**
   * Plebify configuration
   */
  val config: PlebifyConfig = new PlebifyConfig(context.system.settings.config, configName)

  //*******************************************************************************************************************
  // State
  //*******************************************************************************************************************
  /**
   * Engine has stopped
   */
  case object Stopped extends EngineState

  /**
   * Engine is stopping
   */
  case object Stopping extends EngineState

  /**
   * Engine is starting
   */
  case object Starting extends EngineState

  /**
   * Engine has started
   */
  case object Started extends EngineState

  //*******************************************************************************************************************
  // Data
  //*******************************************************************************************************************
  /**
   * No data present
   */
  case object NoData extends EngineData

  trait Progress {
    def sender: ActorRef
    def description: String
  }

  /**
   * Details the job that is being started/stopped
   *
   *  @param idx Index of the current job being started or stopped
   *  @param sender Sender of the initial StartRequest or StopRequest
   */
  case class JobProgress(idx: Int, sender: ActorRef) extends EngineData with Progress {

    /**
     * True if there are more jobs to process
     */
    val hasNext: Boolean = (idx + 1) < config.jobs.size

    /**
     * Current job being started/stopped
     */
    val currentJob = config.jobs(idx)

    /**
     * Next job to process
     */
    lazy val next = if (hasNext) this.copy(idx = idx + 1) else null

    /**
     * Description of this job
     */
    val description = s"job ${currentJob.id}"
  }

  /**
   * Details the connector that is being started/stopped
   *
   *  @param idx Index of the current job being started or stopped
   *  @param sender Sender of the initial StartRequest or StopRequest
   */
  case class ConnectorProgress(idx: Int, sender: ActorRef) extends EngineData with Progress {

    /**
     * True if there are more to process
     */
    val hasNext: Boolean = (idx + 1) < config.connectors.size

    /**
     * Current connector being started/stopped
     */
    val currentConnector = config.connectors(idx)

    /**
     * Next connector to process
     */
    lazy val next = if (hasNext) this.copy(idx = idx + 1) else null

    /**
     * Description of this job
     */
    val description = s"connector ${currentConnector.id}"
  }

  //*******************************************************************************************************************
  // Transitions
  //*******************************************************************************************************************
  startWith(Stopped, NoData)

  when(Stopped) {
    case Event(request: StartRequest, NoData) =>
      val progress = ConnectorProgress(0, sender)
      try {
        log.info(s"Starting Plebify")
        goto(Starting) using startConnector(progress)
      } catch {
        case e: Throwable =>
          sender ! StartResponse(s"Error starting ${progress.description}", Some(e))
          goto(Stopped) using NoData
      }
  }

  when(Starting) {
    // Get a response from a job or connector
    case Event(response: StartResponse, progress: Progress) =>
      try {
        if (response.isSuccess) {
          progress match {
            case connectorProgress: ConnectorProgress => {
              if (connectorProgress.hasNext) {
                stay using startConnector(connectorProgress.next)
              } else {
                // No more connectors, start jobs
                stay using startJob(JobProgress(0, connectorProgress.sender))
              }
            }
            case jobProgress: JobProgress => {
              if (jobProgress.hasNext) {
                stay using startJob(jobProgress.next)
              } else {
                // No more connectors - we have started
                jobProgress.sender ! StartResponse()
                goto(Started) using NoData
              }
            }
          }
        } else {
          throw new Error(response.error.get.getMessage, response.error.get)
        }
      } catch {
        case e: Throwable =>
          progress.sender ! StartResponse(s"Error starting ${progress.description}. ${e.getMessage}", Some(e))
          goto(Stopped) using NoData
      }
  }

  when(Started) {
    case Event(request: StopRequest, NoData) =>
      log.info(s"Stopping Plebify")
      goto(Stopping) using stopJob(JobProgress(0, sender))
  }

  when(Stopping) {
    // Got a response from a job or connector
    case Event(response: StopResponse, progress: Progress) =>
      if (!response.isSuccess) {
        log.error(response.error.get, s"Error while stopping")
      }
      progress match {
        case jobProgress: JobProgress =>
          if (jobProgress.hasNext) {
            stay using stopJob(jobProgress.next)
          } else {
            // No more jobs - stop connectors
            goto(Stopping) using stopConnector(ConnectorProgress(0, jobProgress.sender))
          }

        case connectorProgress: ConnectorProgress =>
          if (connectorProgress.hasNext) {
            stay using stopConnector(connectorProgress.next)
          } else {
            // No more connectors - we have finished
            connectorProgress.sender ! StopResponse
            goto(Stopped) using NoData
          }
      }

  }

  private def startConnector(progress: ConnectorProgress): ConnectorProgress = {
    val connectorConfig = progress.currentConnector
    log.info(s"Starting connector ${connectorConfig.id}")

    val clazz = Class.forName(connectorConfig.factoryClassName)
    val factory = clazz.newInstance().asInstanceOf[ConnectorFactory]
    val connector = factory.create(context.system, connectorConfig)
    connector ! StartRequest()
    progress
  }

  private def stopConnector(progress: ConnectorProgress): ConnectorProgress = {
    val connectorConfig = progress.currentConnector
    val connectorActor = context.actorFor(connectorConfig.actorName)
    connectorActor ! StopRequest()
    progress
  }

  private def startJob(progress: JobProgress): JobProgress = {
    val jobConfig = progress.currentJob
    val jobActor = context.system.actorOf(Props(new Job(jobConfig)), name = jobConfig.actorName)
    jobActor ! StartRequest()
    progress
  }

  private def stopJob(progress: JobProgress): JobProgress = {
    val jobConfig = progress.currentJob
    val jobActor = context.actorFor(jobConfig.actorName)
    jobActor ! StopRequest()
    progress
  }

  //*******************************************************************************************************************
  // Boot up
  //*******************************************************************************************************************
  initialize
}


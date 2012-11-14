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

import scala.concurrent.duration._

import org.mashupbots.plebify.core.config.ConnectorConfig
import org.mashupbots.plebify.core.config.JobConfig

import akka.actor.Actor
import akka.actor.FSM

/**
 * FSM states for [[org.mashupbots.plebify.core.JobWorker]]
 */
sealed trait JobWorkerState

/**
 * FSM data for [[org.mashupbots.plebify.core.JobWorker]]
 */
trait JobWorkerData

/**
 * Worker actor used to process an instance of a [[org.mashupbots.plebify.core.TriggerMessage]].
 *
 * The `JobWorker` is created by the boss [[org.mashupbots.plebify.core.Job]] actor.  Once processing
 * is finished, the `JobWorker` terminates.
 */
class JobWorker(val jobConfig: JobConfig, val event: EventNotification) extends Actor
  with FSM[JobWorkerState, JobWorkerData] with akka.actor.ActorLogging {

  //*******************************************************************************************************************
  // State
  //*******************************************************************************************************************
  /**
   * Job worker has just started and not doing any work
   */
  case object Idle extends JobWorkerState

  /**
   * Job worker has started and is executing tasks
   */
  case object Active extends JobWorkerState

  //*******************************************************************************************************************
  // Data
  //*******************************************************************************************************************
  /**
   * No state data present
   */
  case object Uninitialized extends JobWorkerData

  /**
   * Details the progress of job execution; i.e. which task is being executed.
   *
   *  @param idx Index of the current task being executed
   */
  case class Progress(idx: Int, jobConfig: JobConfig) extends JobWorkerData {

    /**
     * True if we have finished processing and there are no more tasks to execute
     */
    val isFinished: Boolean = idx >= jobConfig.tasks.size

    /**
     * Current task execution configuration to run
     */
    val currentTask = jobConfig.tasks(idx)

    /**
     * Generic error message to identify this task
     */
    val errorMsg = s"Error executing task '${currentTask.taskName}' in connector '${currentTask.connectorId}' " +
      s"for job '${jobConfig.id}'. "
  }

  //*******************************************************************************************************************
  // Transitions
  //*******************************************************************************************************************
  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(_, Uninitialized) => {
      log.info(s"Executing tasks for job '${jobConfig.id}'")
      
      val progress = Progress(0, jobConfig)
      executeTask(progress)
      goto(Active) using progress forMax (5 seconds)
    }

  }

  when(Active) {
    // When we get result from the execution of the current task, process it and move to next task
    case Event(result: TaskExecutionResponse, progress: Progress) => {
      // Post task execution processing
      if (!result.isSuccess) {
        // TODO - configuration options for error handling. Just log for now
        log.error(progress.errorMsg, result.error.get)
      }

      // Execute next task
      val next = progress.copy(idx = progress.idx + 1)
      if (next.isFinished) {
        // Finished - no more tasks to run
        stop(FSM.Normal, Uninitialized)
      } else {
        executeTask(next)
        stay using next forMax (5 seconds)
      }
    }

    case Event(StateTimeout, progress: Progress) => {
      stop(FSM.Failure("Connector timed out"), Uninitialized)
    }

  }

  onTermination {
    case StopEvent(FSM.Normal, state, data) =>
      log.info(s"Task execution for job '${jobConfig.id}' finished")

    case StopEvent(FSM.Shutdown, state, data) =>
      log.info(s"Shutting down task execution job '${jobConfig.id}'")

    case StopEvent(FSM.Failure(cause: String), state, progress: Progress) =>
      log.error(progress.errorMsg + cause)
  }

  /**
   * Sends a message to the connector in order to execute the current task
   *
   * @param progress Progress of executing the current task list
   */
  private def executeTask(progress: Progress) {
    val taskExecutionConfig = progress.currentTask
    val connectorActorName = ConnectorConfig.createActorName(taskExecutionConfig.connectorId)
    val connectorActorRef = context.actorFor(s"../../$connectorActorName")
    if (connectorActorRef.isTerminated) {
      throw new Error(progress.errorMsg + s"Connector '$connectorActorName' is terminated")
    }

    connectorActorRef ! TaskExecutionRequest(taskExecutionConfig)
  }

  //*******************************************************************************************************************
  // Boot up
  //*******************************************************************************************************************
  log.debug(s"Job worker ${jobConfig.id} Actor '${context.self.path.toString}'")
  initialize

}




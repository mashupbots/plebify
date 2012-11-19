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

import org.mashupbots.plebify.core.config.ConnectorConfig
import org.mashupbots.plebify.core.config.JobConfig

import akka.actor.Actor
import akka.actor.FSM
import akka.pattern.ask
import akka.pattern.pipe

/**
 * FSM states for [[org.mashupbots.plebify.core.JobWorker]]
 */
sealed trait JobWorkerState

/**
 * FSM data for [[org.mashupbots.plebify.core.JobWorker]]
 */
trait JobWorkerData

/**
 * Worker actor instanced by [[org.mashupbots.plebify.core.Job]] to execute tasks.
 *
 * ==Starting==
 * When [[org.mashupbots.plebify.core.JobWorker]] starts, it immediately starts processing the specified event
 * notification message.
 *
 * ==Stopping==
 * When processing of event notification message is finished, this actor self terminates.
 *
 * @param jobConfig Job configuration containing the list of tasks to execute
 * @param eventNotification Event notification trigger message
 */
class JobWorker(jobConfig: JobConfig, eventNotification: EventNotification) extends Actor
  with FSM[JobWorkerState, JobWorkerData] with akka.actor.ActorLogging {

  import context.dispatcher

  private case class Start() extends NotificationMessage
  private case class Retry() extends NotificationMessage

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
   * @param idx Index of the current task being executed
   * @param retryCount Current number of retries
   */
  case class Progress(idx: Int = 0, retryCount: Int = 0) extends JobWorkerData {

    /**
     * True if the current task is the last task to execute in the list
     */
    val isLast: Boolean = (idx == jobConfig.tasks.size - 1)

    /**
     * Current task execution configuration to run
     */
    val currentTask = jobConfig.tasks(idx)

    /**
     * Generic error message to identify this task
     */
    val errorMsg = s"Error executing '${currentTask.connectorId}-${currentTask.connectorTask}' for " +
    		s"'${currentTask.name}'."

    /**
     * Returns a new Progress object pointing to the next task and restarts the retry count
     */
    def nextTask(): Progress = {
      this.copy(idx = idx + 1, retryCount = 0)
    }

    /**
     * Returns a new Progress object pointing to the specified task id and restarts the retry count
     */
    def gotoTask(taskId: String): Progress = {
      val newIdx = taskId.toInt - 1
      if (newIdx == -1) throw new Error(s"Task id '${taskId}' not found")
      this.copy(idx = newIdx, retryCount = 0)
    }

    /**
     * Increments the retry count
     */
    def retryTask(): Progress = {
      this.copy(retryCount = retryCount + 1)
    }

  }

  //*******************************************************************************************************************
  // Transitions
  //*******************************************************************************************************************
  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(msg: Start, Uninitialized) => {
      log.info(s"Executing tasks for job '${jobConfig.id}'")
      goto(Active) using executeCurrentTask(Progress())
    }
    case unknown =>
      log.debug("Recieved unknown message while Idle: {}", unknown.toString)
      stay
  }

  when(Active) {
    case Event(result: TaskExecutionResponse, progress: Progress) => {
      processResult(progress, result.error)
    }
    case Event(msg: akka.actor.Status.Failure, progress: Progress) => {
      processResult(progress, Some(msg.cause))
    }
    case Event(result: Retry, progress: Progress) => {
      stay using executeCurrentTask(progress)
    }
    case unknown =>
      log.debug("Recieved unknown message while Active: {}", unknown.toString)
      stay
  }

  onTermination {
    case StopEvent(FSM.Normal, state, progress: Progress) =>
      log.info("JobWorker success")
    case StopEvent(FSM.Failure(cause: Throwable), state, progress: Progress) =>
      log.error(cause, s"JobWorker failed with error: ${cause.getMessage}")
    case _ =>
      log.info(s"JobWorker shutdown")
  }

  /**
   * Sends a [[org.mashupbots.plebify.core.TaskExecutionRequest]] message to the connector in order to execute the
   * current task
   *
   * @param progress Progress of executing the current task list
   * @returns `progress` that was passed in so that we can chain commands
   */
  private def executeCurrentTask(progress: Progress): Progress = {
    val taskConfig = progress.currentTask
    val connectorActorName = ConnectorConfig.createActorName(taskConfig.connectorId)
    val connector = context.actorFor(s"../../$connectorActorName")
    if (connector.isTerminated) {
      throw new Error(progress.errorMsg + s"Connector '$connectorActorName' is terminated")
    }

    val msg = TaskExecutionRequest(jobConfig.id, taskConfig, eventNotification)
    val future = ask(connector, msg)(taskConfig.executionTimeout seconds).mapTo[TaskExecutionResponse]
    future pipeTo self

    progress
  }

  /**
   * Process the result for the future setup in `executeTask`
   *
   * @param progress Current progress
   * @param error Optional error. If empty, assume success.
   */
  private def processResult(progress: Progress, error: Option[Throwable]): State = {
    if (error.isDefined && progress.retryCount < progress.currentTask.maxRetryCount) {
      log.error(error.get, "Retrying {} because of error: {}", progress.currentTask.name, error.get.getMessage)
      context.system.scheduler.scheduleOnce(jobConfig.rescheduleInterval seconds, self, Retry())
      stay using progress.retryTask()
    } else {
      val command = if (error.isEmpty) progress.currentTask.onSuccess else progress.currentTask.onFail
      command match {
        case "next" => {
          if (progress.isLast) {
            stop(FSM.Normal)
          } else {
            stay using executeCurrentTask(progress.nextTask())
          }
        }
        case "success" => {
          stop(FSM.Normal)
        }
        case "error" => {
          stop(FSM.Failure(error.get))
        }
        case taskId: String => {
          stay using executeCurrentTask(progress.gotoTask(taskId))
        }
      }
    }
  }

  //*******************************************************************************************************************
  // Boot up
  //*******************************************************************************************************************
  initialize

  // Kick start processing with a message to ourself
  override def preStart {
    self ! Start()
  }
}




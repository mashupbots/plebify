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

import akka.actor.Actor
import akka.actor.FSM
import org.mashupbots.plebify.core.config.JobConfig
import org.mashupbots.plebify.core.config.ConnectorConfig
import org.mashupbots.plebify.core.config.TaskExecutionConfig
import scala.concurrent.duration._

/**
 * Worker actor used to process an instance of a [[org.mashupbots.plebify.core.TriggerMessage]].
 *
 * The `JobWorker` is created by the boss [[org.mashupbots.plebify.core.Job]] actor.  Once processing
 * is finished, the `JobWorker` terminates.
 */
class JobWorker(val jobConfig: JobConfig, val event: EventNotification) extends Actor with FSM[State, StateData]
  with akka.actor.ActorLogging {

  log.info(s"Running job '${jobConfig.id}' in actor '${context.self.path.toString}'")

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(ExecuteTasks, Uninitialized) => {
      val progress = JobProgress(0, jobConfig)
      executeTask(progress)
      goto(Active) using progress forMax (5 seconds)
    }
  }

  when(Active) {
    /**
     * When we get result from the execution of the current task, process it and move to next task
     */
    case Event(result: TaskExecutionResult, progress: JobProgress) => {
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

    case Event(StateTimeout, progress: JobProgress) => {
      stop(FSM.Failure("Connector timed out"), Uninitialized)
    }
  }

  onTermination {
    case StopEvent(FSM.Normal, state, data) =>
      log.info(s"Finished job '${jobConfig.id}'")

    case StopEvent(FSM.Shutdown, state, data) =>
      log.info(s"Shutting down job '${jobConfig.id}'")

    case StopEvent(FSM.Failure(cause: String), state, progress: JobProgress) =>
      log.error(progress.errorMsg + cause)
  }

  /**
   * Sends a message to the connector in order to execute the current task
   *
   * @param progress Progress of executing the current task list
   */
  private def executeTask(progress: JobProgress) {
    val taskExecutionConfig = progress.current
    val connectorActorName = ConnectorConfig.createActorName(taskExecutionConfig.connectorId)
    val connectorActorRef = context.actorFor(s"../../$connectorActorName")
    if (connectorActorRef.isTerminated) {
      throw new Error(progress.errorMsg + s"Connector '$connectorActorName' is terminated")
    }

    connectorActorRef ! TaskExecution(taskExecutionConfig)
  }

  // Initialize and send a message to ourself to start
  initialize  
  context.self ! ExecuteTasks()
}

/**
 * Identifier messages for the JobWorker actor
 */
trait JobWorkerMessage

/**
 * Executes
 */
case class ExecuteTasks() extends JobWorkerMessage

/**
 * Details the current task that is being performed
 *
 *  @param idx Index of the current task being executed
 */
case class JobProgress(idx: Int, jobConfig: JobConfig) extends StateData {

  /**
   * True if we have finished processing and there are no more tasks to execute
   */
  val isFinished: Boolean = idx >= jobConfig.tasks.size

  /**
   * Current task execution configuration to run
   */
  val current = jobConfig.tasks(idx)

  /**
   * Generic error message to identify this task
   */
  val errorMsg = s"Error executing task '${current.taskName}' in connector '${current.connectorId}' " +
    s"for job '${jobConfig.id}'. "

}

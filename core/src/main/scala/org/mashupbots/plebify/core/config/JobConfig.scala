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
package org.mashupbots.plebify.core.config

import akka.actor.Extension
import com.typesafe.config.Config
import scala.collection.JavaConversions._

/**
 * Job configuration
 *
 * @param id Unique id of this job
 * @param description Description of this job
 * @param initializationTimeout Number of seconds the engine will wait for the
 *   [[org.mashupbots.plebify.core.StartResponse]] message after sending the [[org.mashupbots.plebify.core.StartRequest]]
 * @param maxWorkerCount Maximum number of active [[org.mashupbots.plebify.core.JobWorker]] actors that can be
 *   concurrently active (executing tasks).
 * @param maxWorkerStrategy Strategy to use for handling situations where `maxWorkerCount` has been reached and more
 *   [[org.mashupbots.plebify.core.EventNotification]]s are received.
 * @param queueSize Maximum number of event notification messages to queue if `maxWorkerCount`
 *   has been reached. If 0, all excess messages will be ignored. This setting is only applicable if
 *   `maxWorkerStrategy` is `queue`.
 * @param rescheduleInterval Number of seconds to resechedule an event notification for
 *   re-process if `maxWorkerCount` has been reached. This setting is only applicable if `maxWorkerStrategy` is
 *   `reschedule`.
 * @param events Collection of events to which this job is subscribed. When an event is fired, the tasks are executed
 * @param tasks Work to be performed by this job
 */
case class JobConfig(
  id: String,
  description: String,
  initializationTimeout: Int,
  maxWorkerCount: Int,
  maxWorkerStrategy: MaxWorkerStrategy.Value,
  queueSize: Int,
  rescheduleInterval: Int,
  events: List[EventSubscriptionConfig],
  tasks: List[TaskExecutionConfig]) extends Extension {

  /**
   * Read configuration from AKKA's `application.conf`
   *
   * Defaults:
   *  - `description` = empty string
   *  - `initialization-timeout` = 5 seconds
   *  - `max-worker-count` = 5 workers
   *  - `max-worker-strategy` = queue
   *  - `queue-size` = 100 messages
   *  - `reschedule-interval` = 5 seconds
   *
   * @param config Configuration
   */
  def this(config: Config) = this(
    config.getString("job-id"),
    ConfigUtil.getString(config, "description", ""),
    ConfigUtil.getInt(config, "initialization-timeout", 5),
    ConfigUtil.getInt(config, "max-worker-count", 5),
    MaxWorkerStrategy.withName(ConfigUtil.getString(config, "max-worker-strategy", "queue")),
    ConfigUtil.getInt(config, "queue-size", 100),
    ConfigUtil.getInt(config, "reschedule-interval", 5),
    JobConfig.loadEvents(config, "on"),
    JobConfig.loadTasks(config, "do"))

  /**
   * Name of the actor representing this job
   */
  val actorName = JobConfig.createActorName(id)

  /**
   * Validate this configuration
   */
  def validate() {
    require(!id.isEmpty, "Job Id must contain a value")
    require(!events.isEmpty, s"No 'events' defined in job '$id'")
    require(!tasks.isEmpty, s"No 'tasks' defined in job '$id'")

    require(initializationTimeout > 0, s"'initialization-timeout' for job $id must be greater than 0")
    require(maxWorkerCount > 0, s"'max-worker-count' for job $id must be greater than 0")
    require(queueSize >= 0, s"'queue-size' for job $id must be greater than or equals 0")
    require(rescheduleInterval >= 0, s"'reschedule-interval' for job $id must be greater than or equals 0")

    events.foreach(e => e.validate())
    tasks.foreach(t => t.validate())

    // check onSuccess/onFail for tasks
    tasks.foreach(t => {
      checkCommand(t, "on-success", t.onSuccess)
      checkCommand(t, "on-fail", t.onFail)
    })
  }

  private def checkCommand(t: TaskExecutionConfig, key: String, command: String) {
    command match {
      case "next" => Unit
      case "success" => Unit
      case "fail" => Unit
      case taskNumber: String => {
        val n = try {
          Some(taskNumber.toInt)
        } catch {
          case _: java.lang.NumberFormatException => None
        }
        if (n.isDefined && tasks.isDefinedAt(n.get - 1)) Unit
        else throw new Error(s"Unrecognised command '$command' in '$key' of ${t.name}")
      }
    }
  }

}

/**
 * Defines how a job is to handle event notifications when the maximum number of workers have been reached.
 */
object MaxWorkerStrategy extends Enumeration {
  type MaxWorkerStrategy = Value

  /**
   * Queue new [[org.mashupbots.plebify.core.EventNotification]]s for later processing.
   *
   * Event notifications will be immediately processed when a JobWorker becomes available.
   */
  val Queue = Value("queue")

  /**
   * Reschedule new [[org.mashupbots.plebify.core.EventNotification]]s
   *
   * Event notifications will have to wait for a number seconds before it is retried.
   */
  val Reschedule = Value("reschedule")

}

object JobConfig {

  /**
   * Returns the events that will trigger the running of this job
   *
   * @param config Configuration
   * @param keyPath Dot delimited key path to the trigger configuration
   * @returns List of [[org.mashupbots.plebify.core.config.EventSubscriptionConfig]]
   */
  def loadEvents(config: Config, keyPath: String): List[EventSubscriptionConfig] = {
    val jobId = config.getString("job-id")
    val events = config.getObjectList(keyPath)
    (for (i <- 0 to events.size - 1)
      yield new EventSubscriptionConfig(jobId, i, events(i).toConfig())).toList
  }

  /**
   * Returns the tasks that will be executed when a subscribed event is fired
   *
   * @param config Configuration
   * @param keyPath Dot delimited key path to the action configuration
   * @returns List of [[org.mashupbots.plebify.core.config.TaskExecutionConfig]]
   */
  def loadTasks(config: Config, keyPath: String): List[TaskExecutionConfig] = {
    val jobId = config.getString("job-id")
    val tasks = config.getObjectList(keyPath)
    (for (i <- 0 to tasks.size - 1)
      yield new TaskExecutionConfig(jobId, i, tasks(i).toConfig())).toList
  }

  /**
   * Returns a unique name for a job actor
   *
   * @param jobId Job id
   * @returns Unique name for a job actor of the specified `id`.
   */
  def createActorName(jobId: String): String = s"job-$jobId"
}


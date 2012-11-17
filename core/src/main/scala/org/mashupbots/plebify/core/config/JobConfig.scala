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
  events: Seq[EventSubscriptionConfig],
  tasks: Seq[TaskExecutionConfig]) extends Extension {

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
   * @param id Unique id of this job
   * @param config Configuration
   * @param keyPath Dot delimited key path to this connector configuration
   */
  def this(id: String, config: Config, keyPath: String) = this(
    id,
    ConfigUtil.getString(config, s"$keyPath.description", ""),
    ConfigUtil.getInt(config, s"$keyPath.initialization-timeout", 5),
    ConfigUtil.getInt(config, s"$keyPath.max-worker-count", 5),
    MaxWorkerStrategy.withName(ConfigUtil.getString(config, s"$keyPath.max-worker-strategy", "queue")),
    ConfigUtil.getInt(config, s"$keyPath.queue-size", 100),
    ConfigUtil.getInt(config, s"$keyPath.reschedule-interval", 5),
    JobConfig.loadEvents(config, s"$keyPath.on"),
    JobConfig.loadTasks(config, s"$keyPath.do"))

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
      checkCommand(t.onSuccess)
      checkCommand(t.onFail)
    })
  }

  private def checkCommand(command: String) {
    command match {
      case "next" => Unit
      case "success" => Unit
      case "fail" => Unit
      case taskId: String => {
        if (tasks.exists(t => t.id == taskId)) Unit
        else throw new Error(s"Unrecognised command '$command' in task '$taskId' of job '$id'")
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
   * Note that trigger id forms the key. This implicitly means that a trigger id must be unique
   *
   * @param config Configuration
   * @param keyPath Dot delimited key path to the trigger configuration
   * @returns sequence of event subscription configuration
   */
  def loadEvents(config: Config, keyPath: String): Seq[EventSubscriptionConfig] = {
    val events = config.getObject(keyPath)
    (for (id <- events.keySet()) yield new EventSubscriptionConfig(id, config, s"$keyPath.$id")).toSeq
  }

  /**
   * Returns the tasks that will be executed when a subscribed event is fired
   *
   * Note that action id forms the key. This implicitly means that an action id must be unique
   *
   * @param config Configuration
   * @param keyPath Dot delimited key path to the action configuration
   * @returns Sequence of task configuration
   */
  def loadTasks(config: Config, keyPath: String): Seq[TaskExecutionConfig] = {
    val tasks = config.getObject(keyPath)
    (for (id <- tasks.keySet()) yield new TaskExecutionConfig(id, config, s"$keyPath.$id")).toSeq
  }

  /**
   * Returns a unique name for a job actor
   *
   * @param jobId Job id
   * @returns Unique name for a job actor of the specified `id`.
   */
  def createActorName(jobId: String): String = s"job-$jobId"
}


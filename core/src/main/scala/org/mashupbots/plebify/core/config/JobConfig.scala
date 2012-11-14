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
 * @param events Collection of events to which this job is subscribed. When an event is fired, the tasks are executed
 * @param tasks Work to be performed by this job
 */
case class JobConfig(
  id: String,
  description: String,
  events: Seq[EventSubscriptionConfig],
  tasks: Seq[TaskExecutionConfig]) extends Extension {

  /**
   * Read configuration from AKKA's `application.conf`
   *
   * @param id Unique id of this job
   * @param config Configuration
   * @param keyPath Dot delimited key path to this connector configuration
   */
  def this(id: String, config: Config, keyPath: String) = this(
    id,
    ConfigUtil.getString(config, s"$keyPath.description", ""),
    JobConfig.loadEvents(config, s"$keyPath.on"),
    JobConfig.loadTasks(config, s"$keyPath.do"))

  /**
   * Name of the actor representing this job
   */
  val actorName = JobConfig.createActorName(id)

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
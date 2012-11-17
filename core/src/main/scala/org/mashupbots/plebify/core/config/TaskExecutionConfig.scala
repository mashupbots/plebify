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

/**
 * Task execution configuration for a job.
 *
 * Tasks are executed (or run) by a job when an event to which the job is subscribed fires.  This configuration details
 * how a task should be run.
 *
 * @param id Unique id of this task. Must be in the format `{connector id}-{task}[-optional-text]`.
 * @param description Description of this task
 * @param onSuccess Determines the next step if this task is completed with no errors. Valid values are:
 *  - `next` to execute the next task or terminate with success if this is the last task
 *  - `success` to stop task execution and terminate with no errors
 *  - `fail` to stop task execution and terminate with an error
 *  - id of the next task to run.
 * @param onFail Determines the next step if this task is not completed or has errors. Valid values are:
 *  - `next` to execute the next task or stop with success if this is the last task
 *  - `success` to stop task execution and terminate with no errors
 *  - `fail` to stop task execution and terminate with an error
 *  - id of the next task to run.
 * @param maxRetryCount The maximum number of times a task is re-executed when an error response is received; before the
 *   task is deemed to have failed.
 * @param retryInterval The number of seconds between retries.
 * @param params Parameters for this task
 */
case class TaskExecutionConfig(
  id: String,
  description: String,
  onSuccess: String,
  onFail: String,
  maxRetryCount: Int,
  retryInterval: Int,
  params: Map[String, String]) extends Extension {

  /**
   * Read configuration from AKKA's `application.conf`
   *
   * Defaults:
   *  - descripton = empty string
   *  - on-success = next
   *  - on-error = fail
   *  - max-retry-count = 3
   *  - retry-interval = 3 seconds
   *
   * @param id Unique identifier of this task. Must be in the format `{connector id}-{task}[-optional-text]`.
   * @param config Configuration
   * @param keyPath Dot delimited key path to this task configuration
   */
  def this(id: String, config: Config, keyPath: String) = this(
    id,
    ConfigUtil.getString(config, s"$keyPath.description", ""),
    ConfigUtil.getString(config, s"$keyPath.on-success", "next"),
    ConfigUtil.getString(config, s"$keyPath.on-fail", "stop"),
    ConfigUtil.getInt(config, s"$keyPath.max-retry-count", 3),
    ConfigUtil.getInt(config, s"$keyPath.retry-interval", 3),
    ConfigUtil.getParameters(config, keyPath,
      List("description", "on-success", "on-fail", "max-retry-count", "retry-interval")))

  private val splitId = id.split("-")
  require(splitId.length >= 2, s"Task id '$id' must be in the format 'connector-task'")

  /**
   * Id of the connector that will perform the task
   */
  val connectorId = splitId(0)

  /**
   * Name of the connector task that is to be performed
   */
  val taskName = splitId(1)

  /**
   * Validate this configuration
   */
  def validate() {
    require(!id.isEmpty, "Job Id must contain a value")
    require(!onSuccess.isEmpty, s"'on-success' not specified for task $id")
    require(!onFail.isEmpty, s"'on-fail' not specified for task $id")
    require(maxRetryCount > 0, s"'initialization-timeout' for job $id must be greater than 0")
    require(retryInterval > 0, s"'max-worker-count' for job $id must be greater than 0")
  }
}

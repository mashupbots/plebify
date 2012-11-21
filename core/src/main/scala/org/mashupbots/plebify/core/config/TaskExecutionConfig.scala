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
 * @param jobId Id of the job to which this task belong
 * @param index Index of this event subscription in the List of event subscription for a job
 * @param connectorId Id of the connector containing the task that we wish to execute
 * @param connectorTask Id of the task in the connector that we wish to execute
 * @param description Description of this task
 * @param executionTimeout Number of seconds to wait for a response form the task before declaring a timeout error
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
  jobId: String,
  index: Int,
  connectorId: String,
  connectorTask: String,
  description: String,
  executionTimeout: Int,
  onSuccess: String,
  onFail: String,
  maxRetryCount: Int,
  retryInterval: Int,
  params: Map[String, String]) extends Extension {

  /**
   * Read configuration from AKKA's `application.conf`
   *
   * Defaults:
   *  - description = empty string
   *  - execution-timeout = 5 seconds
   *  - on-success = next
   *  - on-error = fail
   *  - max-retry-count = 3
   *  - retry-interval = 3 seconds
   *
   * @param jobId Id of the job to which this task belong
   * @param index Index of this event subscription in the List of event subscription for a job
   * @param config Raw Akka configuration
   */
  def this(jobId: String, index: Int, config: Config) = this(
    jobId,
    index,
    config.getString("connector-id"),
    config.getString("connector-task"),
    ConfigUtil.getString(config, "description", ""),
    ConfigUtil.getInt(config, "execution-timeout", 5),
    ConfigUtil.getString(config, "on-success", "next"),
    ConfigUtil.getString(config, "on-fail", "fail"),
    ConfigUtil.getInt(config, "max-retry-count", 3),
    ConfigUtil.getInt(config, "retry-interval", 3),
    ConfigUtil.getParameters(config,
      List("connector-id", "connector-task", "description", "execution-timeout",
        "on-success", "on-fail", "max-retry-count", "retry-interval")))

  /**
   * Descriptive name of event subscription that can be used in messages
   */
  val name = s"job '$jobId' task #${index + 1}"

  /**
   * Validate this configuration
   */
  def validate() {
    require(index >= 0, s"Task execution index in job $jobId must be 0 or greater")
    require(!onSuccess.isEmpty, s"'on-success' not specified in $name")
    require(!onFail.isEmpty, s"'on-fail' not specified in $name")
    require(maxRetryCount > 0, s"'initialization-timeout' in $name must be greater than 0")
    require(retryInterval > 0, s"'max-worker-count' in $name must be greater than 0")
  }
}

/**
 * Commands that can be used with onSuccess and onFail
 */
object TaskExecutionCommand {
  /**
   * Go to next task
   */
  val Next = "next"
    
  /**
   * Terminate with success    
   */
  val Success = "success"
    
  /**
   * Terminate with failure
   */
  val Fail = "fail"
}

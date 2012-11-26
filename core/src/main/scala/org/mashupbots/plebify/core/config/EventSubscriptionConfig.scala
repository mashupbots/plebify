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
 * Event subscription configuration for a job.
 *
 * Details the condition that a notification will be sent to the specified job when this event fires.
 *
 * @param jobId Id of the job to which this event subscription belong
 * @param index Index of this event subscription in the List of event subscription for a job
 * @param connectorId Id of the connector containing the event to which we wish to subscribe
 * @param connectorEvent Id of the event in the connector to which we wish to subscribe
 * @param description Description of this event subscription
 * @param initializationTimeout Number of seconds the job will wait for the
 *  [[org.mashupbots.plebify.core.EventSubscriptionResponse]] message after sending the
 *  [[org.mashupbots.plebify.core.EventSubscriptionRequest]].
 * @param params Parameters for this event subscription
 */
case class EventSubscriptionConfig(
  jobId: String,
  index: Int,
  connectorId: String,
  connectorEvent: String,
  description: String,
  initializationTimeout: Int,
  params: Map[String, String]) extends Extension {

  /**
   * Read configuration from AKKA's `application.conf`
   *
   * Defaults:
   *  - `description` = empty string.
   *  - `initialization-timeout` = 30 seconds.
   *
   * @param jobId Id of the job to which this task belong
   * @param index Index of this event subscription in the List of event subscription for a job
   * @param config Raw akka configuration
   */
  def this(jobId: String, index: Int, config: Config) = this(
    jobId,
    index,
    config.getString("connector-id"),
    config.getString("connector-event"),
    ConfigUtil.getString(config, "description", ""),
    ConfigUtil.getInt(config, "initialization-timeout", 30),
    ConfigUtil.getParameters(config, List("connector-id", "connector-event", "description", "initialization-timeout")))

  /**
   * Descriptive name of event subscription that can be used in messages
   */
  val name = s"job '$jobId' event #${index + 1}"

  /**
   * Validate this configuration
   */
  def validate() {
    require(index >= 0, s"Event subscription index in job '$jobId' must be 0 or greater ")
    require(!connectorId.isEmpty, s"'connector-id' in $name must contain a value")
    require(!connectorEvent.isEmpty, s"'connector-event' in $name must contain a value")
    require(initializationTimeout > 0, s"'initialization-timeout' in $name must be greater than 0")
  }
}

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
import org.mashupbots.plebify.core.config.TaskExecutionConfig

/**
 * Reads task execution configuration
 */
trait TaskExecutionConfigReader {
  def connectorConfig: ConnectorConfig
  def taskConfig: TaskExecutionConfig

  /**
   * Lookup the value of a configuration parameter
   *
   * If the key starts with `lookup:` then lookup is performed in the `connectorConfig`.  For example `lookup:my-key`,
   * then `connectorConfig` is search using the key of `my-key`.
   *
   * Otherwise, the lookup is performed on `taskConfig` using the key.
   *
   * @param key Key to lookup
   * @returns The value of the key. If not found or the value is an empty string, an `Error` is thrown.
   */
  def configValueFor(key: String): String = {
    val v = configValueFor(key, "")
    if (v.length == 0) throw new Error(s"'$key' parameter not specified for ${taskConfig.name}")
    else v
  }

  /**
   * Lookup the value of a configuration parameter
   *
   * If the key starts with `lookup:` then lookup is performed in the `connectorConfig`.  For example `lookup:my-key`,
   * then `connectorConfig` is search using the key of `my-key`.
   *
   * Otherwise, the lookup is performed on `taskConfig` using the key.
   *
   * @param key Key to lookup
   * @param default Default value if not found
   * @returns The value of the key or `default` if key not found.
   */
  def configValueFor(key: String, default: String): String = {
    val v = taskConfig.params.get(key)
    if (v.isDefined && v.get.startsWith("lookup:"))
      connectorConfig.params.get(v.get.substring(7)).getOrElse(default)
    else
      v.getOrElse(default)
  }
}

/**
 * Reads event subscription configuration
 */
trait EventSubscriptionConfigReader {
  def connectorConfig: ConnectorConfig
  def request: EventSubscriptionRequest

  /**
   * Lookup the value of a configuration parameter
   *
   * If the key starts with `lookup:` then lookup is performed in the `connectorConfig`.  For example `lookup:my-key`,
   * then `connectorConfig` is search using the key of `my-key`.
   *
   * Otherwise, the lookup is performed on `request` using the key.
   *
   * @param key Key to lookup
   * @returns The value of the key. If not found or the value is an empty string, an `Error` is thrown.
   */
  def configValueFor(key: String): String = {
    val v = configValueFor(key, "")
    if (v.length == 0) throw new Error(s"'$key' parameter not specified for ${request.config.name}")
    else v
  }

  /**
   * Lookup the value of a configuration parameter
   *
   * If the key starts with `lookup:` then lookup is performed in the `connectorConfig`.  For example `lookup:my-key`,
   * then `connectorConfig` is search using the key of `my-key`.
   *
   * Otherwise, the lookup is performed on `request` using the key.
   *
   * @param key Key to lookup
   * @param default Default value if not found
   * @returns The value of the key or `default` if key not found.
   */
  def configValueFor(key: String, default: String): String = {
    val v = request.config.params.get(key)
    if (v.isDefined && v.get.startsWith("lookup:"))
      connectorConfig.params.get(v.get.substring(7)).getOrElse(default)
    else
      v.getOrElse(default)
  }
}

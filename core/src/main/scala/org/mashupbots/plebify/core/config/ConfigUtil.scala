//
// Copyright 2012 Vibul Imtarnasan, David Bolton and Socko contributors.
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

import java.io.File

import scala.collection.JavaConversions._

import com.typesafe.config.Config

/**
 * A utility class for reading AKKA configuration
 */
object ConfigUtil {

  /**
   * Load all child string items of the specified `keyPath` object into map
   *
   * The following
   * {{{
   *  node {
   *    param1 = "1"
   *    param2 = "2"
   *    param3 = "3"
   *  }
   * }}}
   *
   * will be turned into
   * {{{
   *  Map( ["param1", "1"], ["param1", "2"], ["param1", "3"])
   * }}}
   *
   * @param config Configuration
   * @param keyPath Dot delimited key path to this connector configuration
   * @param keysToIgnore Name of keys under `keyPath` to NOT load
   */
  def getParameters(config: Config, keysToIgnore: List[String]): Map[String, String] = {
    config.entrySet()
      .filter(e => !keysToIgnore.contains(e.getKey()))
      .map(e => (e.getKey(), config.getString(e.getKey())))
      .toMap
  }

  /**
   * Returns the specified setting as an string. If setting not specified, then the default is returned.
   */
  def getString(config: Config, name: String, defaultValue: String): String = {
    try {
      val v = config.getString(name)
      if (v == null || v == "") {
        defaultValue
      } else {
        v
      }
    } catch {
      case e: Throwable => defaultValue
    }
  }

  /**
   * Returns an optional string configuration value
   */
  def getOptionalString(config: Config, name: String): Option[String] = {
    try {
      val v = config.getString(name)
      if (v == null || v == "") {
        None
      } else {
        Some(v)
      }
    } catch {
      case e: Throwable => None
    }
  }

  /**
   * Returns the specified setting as an integer. If setting not specified, then the default is returned.
   */
  def getInt(config: Config, name: String, defaultValue: Int): Int = {
    try {
      val v = config.getString(name)
      if (v == null || v == "") {
        defaultValue
      } else {
        config.getInt(name)
      }
    } catch {
      case e: Throwable => defaultValue
    }
  }

  /**
   * Returns the specified setting as an integer. If setting not specified, then the default is returned.
   */
  def getOptionalInt(config: Config, name: String): Option[Int] = {
    try {
      val v = config.getString(name)
      if (v == null || v == "") {
        None
      } else {
        Some(config.getInt(name))
      }
    } catch {
      case e: Throwable => None
    }
  }

  /**
   * Returns the specified setting as a boolean. If setting not specified, then the default is returned.
   */
  def getBoolean(config: Config, name: String, defaultValue: Boolean): Boolean = {
    try {
      val v = config.getString(name)
      if (v == null || v == "") {
        defaultValue
      } else {
        config.getBoolean(name)
      }
    } catch {
      case e: Throwable => defaultValue
    }
  }

  /**
   * Returns the specified setting as a boolean. `None` is returned if setting not specified
   */
  def getOptionalBoolean(config: Config, name: String): Option[Boolean] = {
    try {
      val v = config.getString(name)
      if (v == null || v == "") {
        None
      } else {
        Some(config.getBoolean(name))
      }
    } catch {
      case e: Throwable => None
    }
  }

  /**
   * Returns the specified setting as an string. If setting not specified, then the default is returned.
   */
  def getListString(config: Config, name: String): List[String] = {
    try {
      val v = config.getStringList(name)
      if (v == null || v.length == 0) {
        Nil
      } else {
        v.toList
      }
    } catch {
      case e: Throwable => Nil
    }
  }

}


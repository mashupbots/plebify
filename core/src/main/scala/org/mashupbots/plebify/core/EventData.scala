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

import java.util.Date
import java.text.SimpleDateFormat
import java.util.TimeZone

/**
 * Common definitions and methods for event data handling
 */
object EventData {

  /**
   * Date event was triggered
   */
  val Date = "Date"

  /**
   * Content
   */
  val Content = "Content"

  /**
   * Content Length
   */
  val ContentLength = "Content-Length"

  /**
   * Last modified date in ISO 8601 format
   */
  val LastModified = "Last-Modified"

  /**
   * Content MIME Type
   */
  val ContentType = "Content-Type"

  /**
   * Formats dates as per ISO 8601. Defaults to UTC timestamp.
   *
   * For example: `2007-04-05T14:30Z`
   */
  def dateTimeToString(d: Date): String = {
    val fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    fmt.setTimeZone(TimeZone.getTimeZone("UTC"))
    fmt.format(d)
  }

  /**
   * Parses dates as per ISO 8601
   *
   * For example: `2007-04-05T14:30Z`.
   */
  def stringToDateTime(s: String): Date = {
    val fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    fmt.setTimeZone(TimeZone.getTimeZone("UTC"))
    fmt.parse(s)
  }
}
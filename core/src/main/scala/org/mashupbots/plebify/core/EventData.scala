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
import javax.activation.MimetypesFileTypeMap
import akka.camel.CamelMessage
import scala.util.matching.Regex

/**
 * Common definitions and methods for event data handling
 */
object EventData {

  private[this] val map = new MimetypesFileTypeMap

  /**
   * Unique identifier for this message
   */
  val Id = "Id"

  /**
   * Date event was triggered
   */
  val Date = "Date"

  /**
   * Content
   */
  val Content = "Content"

  /**
   * Last modified date in ISO 8601 format
   */
  val LastModified = "LastModified"

  /**
   * Content MIME Type
   */
  val ContentType = "ContentType"

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

  /**
   * Returns the MIME type from a file name.
   *
   * This implementation uses <a href="http://docs.oracle.com/javase/6/docs/api/javax/activation/MimetypesFileTypeMap.html">
   * `MimetypesFileTypeMap`</a> and relies on the presence of the file extension in a `mime.types` file.
   *
   * See
   *  - https://github.com/klacke/yaws/blob/master/src/mime.types
   *  - http://svn.apache.org/repos/asf/httpd/httpd/trunk/docs/conf/mime.types
   *  - http://download.oracle.com/javaee/5/api/javax/activation/MimetypesFileTypeMap.html
   *  - src/main/resources/META-INF/mime.types
   *
   * @param fileName name of file
   * @returns MIME type. If no matching MIME type is found, `application/octet-stream` is returned.
   */
  def fileNameToMimeType(fileName: String): String = {
    map.getContentType(fileName)
  }

  /**
   * Reads a string value from the camel header
   *
   * @param msg Camel Message
   * @param key name of header field to read
   * @returns string value
   */
  def readCamelHeader(msg: CamelMessage, key: String): String = {
    if (msg.headers.isDefinedAt(key)) {
      val v = msg.headers(key)
      v match {
        case null => ""
        case d: Date => dateTimeToString(d)
        case _ => v.toString
      }
    } else ""
  }

  private val templateKeyRegex = new Regex("\\{\\{([\\d\\w]+)\\}\\}")

  /**
   * Merge data with a template
   *
   * This is a very simple implementation. It finds `{{key}}` and replace it with the value of the `key` found in
   * `data`.
   *
   * @param template template
   * @param data data to merge with the template
   */
  def mergeTemplate(template: String, data: Map[String, String]): String = {
    // Find keys located in the template
    // Group #1 represents the key. Group #0 is the entire matching string
    val keys: List[String] = templateKeyRegex.findAllMatchIn(template).map(m => m.group(1)).toList

    // Replace keys with values; if key not found, just leave the placeholder in there
    val output: String = keys.foldLeft(template)((t, key) => {
      val placeholder = s"{{$key}}"
      t.replace(placeholder, data.getOrElse(key, placeholder))
    })

    output
  }

}
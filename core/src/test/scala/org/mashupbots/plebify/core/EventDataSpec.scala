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

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.BeforeAndAfterAll
import java.util.Date
import java.util.GregorianCalendar
import java.util.Calendar

class EventDataSpec extends WordSpec with ShouldMatchers with GivenWhenThen with BeforeAndAfterAll {

  "EventData" should {

    "convert dates" in {
      val d = new Date()
      val s = EventData.dateTimeToString(d)
      val dd = EventData.stringToDateTime(s)

      val cal = new GregorianCalendar()
      cal.setTime(d)
      cal.set(Calendar.MILLISECOND, 0)

      cal.getTimeInMillis() should equal(dd.getTime())
    }

    "Find MIME Types" in {
      EventData.fileNameToMimeType("test.txt") should be("text/plain")
      EventData.fileNameToMimeType("test.csv") should be("text/csv")
      EventData.fileNameToMimeType("test.xml") should be("application/xml")
      EventData.fileNameToMimeType("test.json") should be("application/json")
      EventData.fileNameToMimeType("test.unknown") should be("application/octet-stream")
    }
  }

  "merge templates" in {
    val data = Map(
      ("one", "1"),
      ("two", "2"),
      ("t-h_r$e^e&", "3"))

    val template = "start {{one}} {{two}} middle {{t-h_r$e^e&}} {{four}} end"

    EventData.mergeTemplate(template, data) should be("start 1 2 middle 3 {{four}} end")
  }
}
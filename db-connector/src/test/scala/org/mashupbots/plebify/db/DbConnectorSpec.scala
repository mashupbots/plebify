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
package org.mashupbots.plebify.db

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import org.mashupbots.plebify.core.ConnectorFactory
import org.mashupbots.plebify.core.Engine
import org.mashupbots.plebify.core.EventData
import org.mashupbots.plebify.core.EventNotification
import org.mashupbots.plebify.core.EventSubscriptionRequest
import org.mashupbots.plebify.core.EventSubscriptionResponse
import org.mashupbots.plebify.core.StartRequest
import org.mashupbots.plebify.core.StartResponse
import org.mashupbots.plebify.core.TaskExecutionRequest
import org.mashupbots.plebify.core.TaskExecutionResponse
import org.mashupbots.plebify.core.config.ConnectorConfig
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.slf4j.LoggerFactory
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import org.mashupbots.plebify.core.config.ConfigUtil
import java.sql.Connection
import org.apache.commons.dbcp.BasicDataSource
import java.sql.Statement
import java.sql.ResultSet

/**
 * Tests for [[org.mashupbots.plebify.mail.DbConnector]]
 *
 * Note that before you run the test, you must create a file called `plebify-tests-config.txt` in your home
 * directory.
 *
 * The file must contain the following configuration:
 *
 * {{{
 * # Class of your database's JDBC driver. e.g. 'com.mysql.jdbc.Driver'
 * datasource-driver=
 *
 * # Connection URL for your jdbc driver. e.g. 'jdbc:mysql://localhost:3306/test'
 * datasource-url =
 *
 * # Database login user id that has read/write permission in that database
 * datasource-user =
 *
 * # Database login password
 * datasource-password =
 *
 * }}}
 *
 * You must also run the following SQL to create create the tables that will be used
 *
 * {{{
 * CREATE TABLE table_a (
 * pk INT NOT NULL AUTO_INCREMENT,
 * code VARCHAR(45) NOT NULL,
 * digits INT NOT NULL,
 * PRIMARY KEY (pk) )
 *
 * CREATE TABLE table_b (
 * pk INT NOT NULL AUTO_INCREMENT,
 * msg_id VARCHAR(100) NOT NULL,
 * code VARCHAR(45) NOT NULL,
 * digits INT NOT NULL,
 * data VARCHAR(4000) NOT NULL,
 * PRIMARY KEY (pk) )
 *
 * }}}
 */
class DbConnectorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpec
  with MustMatchers with BeforeAndAfterAll {

  println(DbConnectorSpec.cfg)
  val log = LoggerFactory.getLogger("DbConnectorSpec")
  def this() = this(ActorSystem("DbConnectorSpec", ConfigFactory.parseString(DbConnectorSpec.cfg)))

  "Database Connector" must {

    "be able to read and write records" in {
      DbConnectorSpec.execute("delete from table_a")
      DbConnectorSpec.execute("delete from table_b")

      // Start
      val engine = system.actorOf(Props(new Engine(configName = "read-write-records")), "read-write-records")
      engine ! StartRequest()
      expectMsgPF(5 seconds) {
        case m: StartResponse => {
          m.isSuccess must be(true)
        }
      }

      // Create some entries to table_a
      DbConnectorSpec.execute("insert into table_a (code, digits) values ('aaa', 1)")
      DbConnectorSpec.execute("insert into table_a (code, digits) values ('bbb', 2)")
      DbConnectorSpec.execute("insert into table_a (code, digits) values ('ccc', 3)")
      DbConnectorSpec.execute("insert into table_a (code, digits) values ('aaa', 4)")
      DbConnectorSpec.execute("insert into table_a (code, digits) values ('aaa', 5)")
      DbConnectorSpec.execute("insert into table_a (code, digits) values ('aaa', 6)")
      DbConnectorSpec.execute("insert into table_a (code, digits) values ('aaa', 7)")

      // Wait for processing
      Thread.sleep(4000)

      // Check the results in table_b
      val rA = DbConnectorSpec.executeQuery("select * from table_a")
      rA.size must be(2)

      val rB = DbConnectorSpec.executeQuery("select * from table_b")
      rB.size must be(1)
      rB(0)("code") must be ("aaa") 
      rB(0)("digits") must be (1) 
    }

  }
}

/**
 * Companion class
 */
object DbConnectorSpec {

  val readWriteRecords = """
	read-write-records {
      connectors = [{
          connector-id = "db"
          factory-class-name = "org.mashupbots.plebify.db.DbConnectorFactory"
          ds1-datasource-driver = "{datasource-driver}"
          ds1-datasource-url = "{datasource-url}"
          ds1-datasource-user = "{datasource-user}"
          ds1-datasource-password = "{datasource-password}"
        }]
      jobs = [{
        job-id = "job1"
          on = [{
              connector-id = "db"
              connector-event = "query"
              datasource = "ds1"
              sql = "select * from table_a where code = 'aaa' order by code, digits"
              initial-delay = "1"
              interval = "1"
	        }]
          do = [{
              connector-id = "db"
              connector-task = "execute"
              datasource = "ds1"
              sql = "insert into table_b (msg_id, code, digits, data) values ('{{Id}}', '{{row1-code}}', {{row1-digits}}, '{{Content}}') "
	        },{
              connector-id = "db"
              connector-task = "execute"
              datasource = "ds1"
              sql = "delete from table_a where code = 'aaa'"
	        }]
        }]
	}
    
	akka {
	  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
	  loglevel = "DEBUG"
	}    
    """

  val template = List(readWriteRecords).mkString("\n")
  val settings = ConfigUtil.parseNameValueTextFile(System.getProperty("user.home"))
  val cfg = ConfigUtil.mergeNameValueTextFile(template, System.getProperty("user.home"))

  val datasource = new BasicDataSource()
  datasource.setDriverClassName(settings("datasource-driver"));
  datasource.setUrl(settings("datasource-url"));
  datasource.setUsername(settings("datasource-user"));
  datasource.setPassword(settings("datasource-password"));

  def execute(sql: String): Int = {
    val conn = datasource.getConnection()
    val stmt: Statement = conn.createStatement()
    val rows = stmt.executeUpdate(sql)
    stmt.close()
    conn.close()

    rows
  }

  def executeQuery(sql: String): List[Map[String, Any]] = {
    val results = new ListBuffer[Map[String, Any]]()
    val conn = datasource.getConnection()
    val stmt: Statement = conn.createStatement()
    val rs: ResultSet = stmt.executeQuery(sql)
    val metaData = rs.getMetaData
    while (rs.next()) {
      val m = for {
        i <- 1 to (metaData.getColumnCount())
        c = metaData.getColumnName(i)
      } yield (c, rs.getObject(c))
      results += m.toMap
    }
    rs.close()
    stmt.close()
    conn.close()

    results.toList
  }

}



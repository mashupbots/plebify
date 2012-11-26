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

import java.util.Properties

import org.apache.camel.impl.JndiRegistry
import org.apache.commons.dbcp.DriverManagerConnectionFactory
import org.apache.commons.dbcp.PoolableConnectionFactory
import org.apache.commons.dbcp.PoolingDataSource
import org.apache.commons.pool.impl.GenericObjectPool
import org.mashupbots.plebify.core.DefaultConnector
import org.mashupbots.plebify.core.EventSubscriptionRequest
import org.mashupbots.plebify.core.StartRequest
import org.mashupbots.plebify.core.StartResponse
import org.mashupbots.plebify.core.StartResponse.apply
import org.mashupbots.plebify.core.TaskExecutionRequest
import org.mashupbots.plebify.core.config.ConnectorConfig

import akka.actor.ActorRef
import akka.actor.Props
import akka.camel.CamelExtension

/**
 * Connector to databases
 *
 * ==Parameters==
 *  - '''datasource-XXX-driver''': Class name of JDBC database driver. For example, `"com.mysql.jdbc.Driver"`
 *  - '''datasource-XXX-url''': Name name of database driver. For example, `"jdbc:mysql://localhost:3306/student"`
 *  - '''datasource-XXX-user''': Username for accessing the database
 *  - '''datasource-XXX-password''': Password for accessing the database
 *
 * ==Events==
 *  - '''record-found''': When records matching a SQL select statement are found.
 *    See [[[org.mashupbots.plebify.db.RecordFoundEvent]]]
 *
 * ==Tasks==
 *  - '''insert-record''': Save data to the specified file. See [[[org.mashupbots.plebify.db.InsertRecordTask]]].
 */
class DbConnector(connectorConfig: ConnectorConfig) extends DefaultConnector {

  log.debug("DbConnector created with {}", connectorConfig)

  /**
   * Override this method to register our connection pool
   *
   * @param message to process
   * @returns Start response to return to the sender
   */
  override def onStart(msg: StartRequest): StartResponse = {

    try {
      val camel = CamelExtension(context.system)
      val registry = camel.context.getRegistry().asInstanceOf[JndiRegistry]

      // See http://svn.apache.org/repos/asf/commons/proper/dbcp/branches/TEST_DBCP_1_3_BRANCH/doc/ManualPoolingDataSourceExample.java
      val dataSourceNames = DbConnector.extractDatasourceNames(connectorConfig)
      dataSourceNames.foreach(dataSourceName => {

        // Load underlying JDBC driver
        Class.forName(connectorConfig.params(s"datasource-${dataSourceName}-driver"))

        //
        // First, we'll need a ObjectPool that serves as the
        // actual pool of connections.
        //
        // We'll use a GenericObjectPool instance, although
        // any ObjectPool implementation will suffice.
        //
        val connectionPool = new GenericObjectPool(null)

        //
        // Next, we'll create a ConnectionFactory that the
        // pool will use to create Connections.
        // We'll use the DriverManagerConnectionFactory,
        // using the connect string passed in the command line
        // arguments.
        //        
        val connectionProps = new Properties()
        val inputProps = connectorConfig.params.filter {
          case (k, v) => k.startsWith(s"datasource-${dataSourceName}") && !k.endsWith("-driver") && !k.endsWith("-url")
        }
        inputProps.foreach {
          case (k, v) =>
            val propName = k.substring(k.lastIndexOf("-") + 1)
            val propValue = v
            connectionProps.put(propName, propValue)
        }
        val connectionFactory = new DriverManagerConnectionFactory(
          connectorConfig.params(s"datasource-${dataSourceName}-url"), connectionProps)

        //
        // Now we'll create the PoolableConnectionFactory, which wraps
        // the "real" Connections created by the ConnectionFactory with
        // the classes that implement the pooling functionality.
        //        
        val poolableConnectionFactory = new PoolableConnectionFactory(
          connectionFactory, connectionPool, null, null, false, true)

        //
        // Finally, we create the PoolingDriver itself,
        // passing in the object pool we created.
        //
        val dataSource = new PoolingDataSource(connectionPool);

        //
        // Register the datasource with camel so it can be used by consumers and producers
        //
        if (registry.lookup(dataSourceName) == null) {
          log.debug("Registering datasource {}", dataSourceName)
          registry.bind(dataSourceName, dataSource)
        }

      })

      StartResponse()
    } catch {
      case e: Throwable => StartResponse(Some(e))
    }

  }

  def instanceEventActor(req: EventSubscriptionRequest): ActorRef = {
    req.config.connectorEvent match {
      case DbConnector.SqlQueryEvent =>
        context.actorOf(Props(new SqlQueryEvent(req)), name = createActorName(req.config))
      case unknown =>
        throw new Error(s"Unrecognised event $unknown")
    }
  }

  def instanceTaskActor(req: TaskExecutionRequest): ActorRef = {
    req.config.connectorTask match {
      case DbConnector.ExecuteSqlTask =>
        context.actorOf(Props(new ExecuteSqlTask(req.config)), createActorName(req.config))
      case unknown =>
        throw new Error(s"Unrecognised task $unknown")
    }
  }
}

/**
 * Companion object of FileConnector class.
 */
object DbConnector {

  val SqlQueryEvent = "query"

  val ExecuteSqlTask = "execute"

  def extractDatasourceNames(connectorConfig: ConnectorConfig): List[String] = {
    (for {
      k <- connectorConfig.params.keys
      if k.startsWith("datasource-") && k.endsWith("-driver")
    } yield k.substring(11, k.lastIndexOf("-") - 11)).toList
  }
}
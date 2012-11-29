Plebify User Manual
*******************

Download and Installation
=========================
- `Download <https://github.com/mashupbots/plebify/downloads>`_

- Unzip the package into ``~/plebify`` (for example)

- Configure

  - Edit ``~/plebify/config/application.conf``. See examples in ``~/plebify/examples`` for inspriation.
  - Review logging configuration in ``~/plebify/config/logback.xml``.

- Run

  - ``cd ~\plbefiy\bin``
  - ``.\start``


Configuration
=============

The Plebify configuration file is located at ``~/plebify/config/application.conf``.

For example:

::

  plebify {
    connectors = [{
        connector-id = "conn1"
        factory-class-name = "connector.factory.class.path"
      },{
        connector-id = "conn2"
        factory-class-name = "connector.factory.class.path"
      }]
    jobs = [{
        job-id = "job1"
        on = [{
            connector-id = "conn1"
            connector-event = "event1"
        }]
        do = [{
            connector-id = "conn1"
            connector-task = "task1"
        }]
      },{
        job-id = "job2"
        on = [{
            connector-id = "conn2"
            connector-event = "event1"
        }]
        do = [{
            connector-id = "conn1"
            connector-task = "task2"
        },{
            connector-id = "conn2"
            connector-task = "task2"
        }]
      }]
  }
    
  akka {
    event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
    loglevel = "INFO"
  }


The format of the file is `HOCON <https://github.com/typesafehub/config/blob/master/HOCON.md>`_.

The file is split into 2 parts: 

- ``plebify`` 
  Our application configuration as detailed below.
  
- ``akka``
  AKKA framework configuration.  For everyday use, you will not need to worry about this setting.


Connectors
----------

You can specify one or more connectors between the ``[ ]``.  Each connector must be enclosed by ``{ }`` 
and separated by comma. Each connector setting must be on a new line.

- id
  Unique name of this connector

- description
  Optional description of this connector

- factory-class-name 
  Full class path to actor factory class of a connector. See connector reference for details.

- initialization-timeout 
  Optional number of seconds to wait for a connector to successfully start up. Defaults to ``30`` seconds.

- "Connector Specific Settings"
  These are specified in the connector reference.


Jobs
----

Like connectors, you can specify one or more jobs between the ``[ ]``.  Each job must be enclosed by ``{ }`` 
and separated by comma. Each job setting must be on a new line.

- id
  Unique id of this job

- description
  Optional description of this job

- initialization-timeout 
  Optional number of seconds to wait for this job to start before timeout. Defaults to ``30`` seconds.

- max-worker-count 
  Optional maximum number of active job worker actors that can be concurrently active (executing tasks). 
  Defaults to ``5``.

- max-worker-strategy 
  Optional strategy to use for handling situations where `max-worker-count` has been reached and more events 
  are received. Options are ``queue`` the event in the job (default) or ``reschedule`` the event to be 
  processed by the job later.

- queue-size 
  Optional maximum number of event notification messages to queue if `max-worker-count` has been reached and
  ``max-worker-strategy`` is set to ``reschedule``. If 0, all excess messages will be ignored; i.e. no queue. 
  Default to ``100``.

- reschedule-interval 
  Optional number of seconds to resechedule an event notification for re-processing if `max-worker-count` has 
  been reached and ``max-worker-strategy`` is set to ``reschedule``. Defaults to ``5`` seconds.

- on
  Connection of events to subscribe to for this job.  See below for more details

- do
  Connection of tasks to execute to for this job.  See below for more details


on Events
---------

You can specify one or more events between the ``[ ]``.  Each event must be enclosed by ``{ }`` 
and separated by comma. Each event setting must be on a new line.

- connector-id 
  Id of the connector containing the event to which we wish to subscribe. This must be present in
  the connectors section of the confguration file.

- connector-event 
  Name of the event in the connector to which we wish to subscribe. See connector reference for 
  details.

- description 
  Optional description of this event subscription

- initialization-timeout 
  Optional number of seconds the job will wait for a subscription to be setup before timing out.
  Defaults to ``30`` seconds.

- "Connector Events Specific Settings"
  These are specified in the connector reference.



do Tasks
--------

You can specify one or more tasks between the ``[ ]``.  Each task must be enclosed by ``{ }`` 
and separated by comma. Each task setting must be on a new line.

- connector-id 
  Id of the connector containing the event to which we wish to subscribe. This must be present in
  the connectors section of the confguration file.

- connector-task
  Name of the task in the connector to which we wish to execute. See connector reference for 
  details.

- description 
  Optional description of this task

- execution-timeout 
  Optional number of seconds the job will wait for a task to execute before timing out.
  Defaults to ``30`` seconds.

- on-success
  Optional next step if this task is completed **without** errors. Valid values are:

  - ``next`` to execute the next task or terminate with success if this is the last task. This is the default.
  - ``success`` to stop task execution and terminate with no errors
  - ``fail`` to stop task execution and terminate with an error
  - Number of the next task to run; with 1 being the 1st task in the collection.

- on-fail
  Optional next step if this task is completed **with** errors. Valid values are:

  - ``next`` to execute the next task or terminate with success if this is the last task. 
  - ``success`` to stop task execution and terminate with no errors
  - ``fail`` to stop task execution and terminate with an error. This is the default.
  - Number of the next task to run; with 1 being the 1st task in the collection.

- max-retry-count
  Optional maximum number of times a task is re-executed when an error response is received; before the
  task is deemed to have failed. Default is ``3`` times.

- retry-interval 
  Optional number of seconds between retry attempts. Defaults to ``3`` seconds.

- "Connector Task Specific Settings"
  These are specified in the connector reference.



Event Data
==========

When a event fires, associated data is provided in the notification that is sent to all tasks.

Common Fields in the event data includes:

  Id
    Unique identifier for this message

  Date
    Timestamp the event was triggered

  Content
    Data that was received

  LastModified
    Optional timestamp when the data was last modified

  ContentType
    MIME type of the content

Connector event specific fields may optionally be supplied.  These are defined in the connector reference.


**Notes**

- All event data is stored as a string.  

- Dates are transformed into ISO 8601 format: ``2007-04-05T14:30:00Z``



Connector Reference
===================

- `Database Connector <https://github.com/mashupbots/plebify/blob/master/docs/UserManual_DbConnector.rst>`_
   Connects Plebify to your relational SQL database via JDBC.
   
- `File System Connector <https://github.com/mashupbots/plebify/blob/master/docs/UserManual_FileConnector.rst>`_
   Connects Plebify to the local file system.

- `HTTP Connector <https://github.com/mashupbots/plebify/blob/master/docs/UserManual_HttpConnector.rst>`_
   Connects Plebify to systems using HTTP and Websocket protocols.

- `Mail Connector <https://github.com/mashupbots/plebify/blob/master/docs/UserManual_HttpConnector.rst>`_
   Connects Plebify to email.



Using Plebify as a library
==========================
You can very easily add Plebify to your own AKKA application by including the Plebify JAR files and
dependancies.

We will shortly be publishing Plebify to maven repository for your convinience.




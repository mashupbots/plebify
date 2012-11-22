plebify
=======

A simple event triggered task runner.



What does Plebify do?
---------------------
Most large application that we've worked on always have associated manual tasks such as:

  - checking log files for errors
  - running queries and emailing out the results
  - the dreaded data entry  

Due to resource and budget constraints, these tasks never gets automated.  Rather, they tend to
get delegated to `Plebs <http://en.wikipedia.org/wiki/Plebs>`_.  The lower you are in the team,
the more likely it is you will be doing these tasks.

Plebify aims to be your virtual pleb.

For example:

1. Scan a directory for the log file and email it to you if the file is missing or the file contains
   the word "error".

2. When a new record is added to a products database table, send email notifications to product
   managers and add an entry to a new products RSS feed.

3. When an email arrives containing an order form, add it to the orders database table.


High-level Design and Architecture
----------------------------------
At its core, Plebify is rules based a data exchange for applications.

The rules are defined as jobs.  Each job comprise of events and tasks.  When a job's event fires,
its tasks are executed.

For example:

 - when a file is created on the file system, email it to person A and HTTP post it to a REST endpoint 
   of elasticsearch.
 - when a database is created, email the new record to a group email
 - when and email is received, write it to a database record

To assist with system integration, we want to use `Apache Camel <http://camel.apache.org/>`_.  
It is a proven product that supports many different types of transports and data formats.

We decided to implemented Plebify using Scala 2.10 and Akka 2.1 because they support Camel and the actor 
model.

MORE INFO TO COME....



Using Plebify from the Command Line
-----------------------------------
TO DO



Using Plebify as a Library
--------------------------
TO DO




Building Plebify
----------------
- Download and install `sbt <http://www.scala-sbt.org/>`_ 0.12.1.

- Get our the code from github

- Run ``sbt`` from the root source directory



Setting up Eclipse
------------------
- We are currently using Scala 2.10 RC2 and JDK7.

- We are currently using Eclipse 4.2 Juno with `Scala IDE <http://scala-ide.org/>`_ 2.1.0 Milestone 2.

- Generate eclipse project files: ``sbt eclispse``

- Start ``Eclipse``

- From the top menu, select ``File`` | ``Import``

  - Select ``General`` | ``Existing Projects into Workspace``.
  - Click ``Next``.
  - Select the ``plebify`` directory as the root
  - Select all sub projects
  - Click ``Finish``



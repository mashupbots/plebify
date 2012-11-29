Plebify User Manual - Database Connector
****************************************

The database connector interacts with your relational SQL database via JDBC.

Connector Settings
==================

Parameters
----------

- **factory-class-name**

  ``org.mashupbots.plebify.db.DbConnectorFactory``

- **XXX-datasource-driver**

  Class name of JDBC database driver. For example, `"com.mysql.jdbc.Driver"`

- **XXX-datasource-url**

  Name name of database driver. For example, `"jdbc:mysql://localhost:3306/student"`

- **XXX-datasource-user**

  Username for accessing the database

- **XXX-datasource-password**

  Password for accessing the database


Note that ``XXX`` is the name of your datasource. It will be used in events and tasks.

You may have more than 1 datasource, just make sure that you give them different names. The following
examples illustrates 2 databases named ``prod`` and ``test``.

::

  prod-datasource-driver = "com.mysql.jdbc.Driver"
  prod-datasource-url = "jdbc:mysql://192.168.1.1:3306/prod"
  prod-datasource-user = "userA"
  prod-datasource-password = "secretA"
  test-datasource-driver = "com.mysql.jdbc.Driver"
  test-datasource-url = "jdbc:mysql://192.168.1.2:3306/test"
  test-datasource-user = "userB"
  test-datasource-password = "secretB"


"record-found" Event
====================
Fires with a record is found when running the specified SQL query.

Settings
--------

- **datasource**

  Name of datasource as specified in the connector config

- **sql**

  SQL statement to execute

- **max-rows**

  Optional maximum number of rows to be returned in a query. Defaults to ``100`` if not supplied.

- **initial-delay**

  Optional number of seconds before polling is started. Defaults to ``60`` seconds.

- **interval**

  Optional number of seconds between polling for the database. Defaults to ``300`` seconds.

- **sql-timeout**

  Optional number of seconds to wait for query to return. Defaults to ``30`` seconds.


Event Data
----------

- **Date**

  Timestamp when event occurred

- **Content**

  Rows found in text format

- **ContentType**

  ``text/plain``

- **row1-cname**

  Value for row ``#1`` column ``cname``. The row number and column name is dynamic and depends on the
  query. For example, ``select letters, digits from tableX`` will return fields: ``row1-letters``,
  ``row1-digits``, ``row2-letters``, ``row2-digits``, etc.



"insert-record" Task
====================
Executes an ``insert``, ``update`` or ``delete`` command.

Settings
--------

- **datasource**

  Name of datasource as specified in the connector config

- **sql**

  SQL statement to execute. Put event data into your SQL with ``{{event data item name}}``. For example,
  to put the contents, add ``{{Contents}}``






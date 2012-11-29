Plebify User Manual - File System Connector
*******************************************

The file system connector interacts with the file system.

Connector Settings
==================

Parameters
----------

  factory-class-name
    ``org.mashupbots.plebify.file.FileConnectorFactory``


"created" Event
===============
Fires with a file is created in the specified directory.

Parameters
----------

  uri
    `Apache Camel file component <http://camel.apache.org/file2.html>`_ URI. Common and consumer options are
    applicable.

  mime-type
    Optional mime type. If not specified, one will be extrapolated using the file name extension.

  contains
    Optional comma separated list of words or phrases to match before the event fires. For example,
    ``error, warn`` to match files containing the word ``error`` or ``warn``.

  matches
    Optional regular expression to match before the event fires. For example:
    ``"^([\\s\\d\\w]*(ERROR|WARN)[\\s\\d\\w]*)$"`` to match files containing the words ``ERROR`` or ``WARN``.


Event Data
----------

  Date
    Timestamp when event occurred

  Content
    Contents of the file

  ContentType
    MIME Type

  LastModified
    When the file was last changed

  FileName
    Name of file without path



"save" Task
===========

Saves the event data to file.

Parameters
----------

  uri
    `Apache Camel file component <http://camel.apache.org/file2.html>`_ URI. Common and producer options are
    applicable.

  file-name-field
    Optional name of field in the event data that contains the name of the file to use. If not supplied, or 
    value is empty, then the default file name will be used as specified in the ``uri``.

  template
    Optional template for the contents of the file. If not specified, the value of ``Contents`` will be saved.




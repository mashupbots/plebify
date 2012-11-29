Plebify User Manual - HTTP Connector
************************************

The HTTP connector allows interactions with system over the HTTP and Websocket protocols.

HTTP requests are supported via the ``request-received`` event and ``send-request`` task.

Websocket transmissions are supported via the ``frame-received`` event and ``send-frame`` task.


Connector Settings
==================

Parameters
----------

  factory-class-name
    ``org.mashupbots.plebify.http.HttpConnectorFactory``

  websocket-server-XXX 
    `Apache Camel websocket component <http://camel.apache.org/websocket.html>`_ URI. Common and producer options are
    applicable. For example, ``websocket://localhost:9999/out``.

    The parameter name is used in the send-frame task.

    XXX is your unique id for the server.  For example, if you have 2 servers, you may specify them as

::

    websocket-server-1 = websocket://localhost:9999/out
    websocket-server-2 = websocket://localhost:8888/out

   

"frame-received" Event
========================

This event starts a web socket client that connects to the specified websocket server. When a websocket text 
frame is received, an event is fired.

Parameters
----------

  uri
    `Apache Camel jetty component <http://camel.apache.org/jetty.html>`_ URI. Common and consumer options are
    applicable. For example: ``jetty:http://localhost:8877/in``

  mime-type
    Optional mime type of the incoming text data. Defaults to `text/plain`.

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
    Body of the text frame. Currently, only text email is supported. Attachments will be ignored.

  ContentType
    MIME Type set to `text/plain` by default



"request-received" Event
========================

Fires with a HTTP request is received on the specified endpoint.

Parameters
----------

  uri
    `Apache Camel jetty component <http://camel.apache.org/jetty.html>`_ URI. Common and consumer options are
    applicable. For example: ``jetty:http://localhost:8877/in``

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
    Contents of the HTTP request.

  ContentType
    MIME Type set to `text/plain` by default

  HttpUri
    URI of incoming request

  HttpMethod
    HTTP method. e.g. GET, POST.

  HttpPath
    Path part of the URI

  HttpQuery
    Query part of URI

  HttpField_*
    HTTP headers and posted form data fields. For example `User-Agent` will be stored as ``HttpField_User-Agent``



"send-frame" Task
===================

Starts a websocket server and broadcasts frames all clients connected to this server.

Parameters
----------

  websocket-server
    Name of the websocket server as specified in the connector settings.

  template
    Optional template for the data to send. If not specified, value of `Contents` will be sent.



"send-request" Task
===================

Sends an HTTP request to the specified endpoint.

Parameters
----------

  uri
    `Apache Camel http component <http://camel.apache.org/http.html>`_ URI. Common and producer options are
    applicable.

  method
    HTTP method. e.g. GET, POST

  template
    Optional template for the post/put data. If not specified, value of `Contents` will be posted.




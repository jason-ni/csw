package csw.event.client.internal.commons

import csw.logging.client.scaladsl.LoggerFactory

/**
 * All the logs generated from location service will have a fixed componentName, which is the value of "event-service-lib".
 * The componentName helps in production to filter out logs from a particular component and this case, it helps to filter out logs
 * generated from location service.
 */
private[event] object EventServiceLogger extends LoggerFactory("event-service-lib")

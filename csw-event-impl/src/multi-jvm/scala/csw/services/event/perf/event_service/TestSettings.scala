package csw.services.event.perf.event_service

final case class TestSettings(
    testName: String,
    totalTestMsgs: Long,
    payloadSize: Int,
    publisherSubscriberPairs: Int,
    singlePublisher: Boolean
) {
  // data based on measurement
  def totalSize: Int = payloadSize + 97
}
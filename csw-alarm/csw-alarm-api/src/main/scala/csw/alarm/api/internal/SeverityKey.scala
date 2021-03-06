package csw.alarm.api.internal

import csw.alarm.api.internal.Separators.KeySeparator
import csw.alarm.api.models.Key

import scala.language.implicitConversions

private[alarm] case class SeverityKey(value: String)

private[alarm] object SeverityKey {
  implicit def fromAlarmKey(alarmKey: Key): SeverityKey = SeverityKey(s"severity$KeySeparator" + alarmKey.value)

  def fromMetadataKey(metadataKey: MetadataKey): SeverityKey =
    SeverityKey(s"severity$KeySeparator" + metadataKey.value.stripPrefix(s"metadata$KeySeparator"))
}

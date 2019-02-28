package csw.event.client.pb

import csw.params.core.formats.MiscJsonFormats
import csw.params.core.generics.{KeyType, Parameter}
import csw.params.core.models._
import csw.time.core.models.{TAITime, UTCTime}
import csw_protobuf.parameter.PbParameter
import play.api.libs.json.Format
import scalapb.TypeMapper

import scala.reflect.ClassTag

object TypeMapperFactory extends MiscJsonFormats {

  def make(keyType: KeyType[_]): TypeMapper[PbParameter, Parameter[_]] = keyType match {
    case KeyType.ChoiceKey       ⇒ typeMapper[Choice]
    case KeyType.RaDecKey        ⇒ typeMapper[RaDec]
    case KeyType.StringKey       ⇒ typeMapper[String]
    case KeyType.StructKey       ⇒ typeMapper[Struct]
    case KeyType.UTCTimeKey      ⇒ typeMapper[UTCTime]
    case KeyType.TAITimeKey      ⇒ typeMapper[TAITime]
    case KeyType.BooleanKey      ⇒ typeMapper[Boolean]
    case KeyType.CharKey         ⇒ typeMapper[Char]
    case KeyType.ByteKey         ⇒ typeMapper[Byte]
    case KeyType.ShortKey        ⇒ typeMapper[Short]
    case KeyType.LongKey         ⇒ typeMapper[Long]
    case KeyType.IntKey          ⇒ typeMapper[Int]
    case KeyType.FloatKey        ⇒ typeMapper[Float]
    case KeyType.DoubleKey       ⇒ typeMapper[Double]
    case KeyType.ByteArrayKey    ⇒ typeMapper[ArrayData[Byte]]
    case KeyType.ShortArrayKey   ⇒ typeMapper[ArrayData[Short]]
    case KeyType.LongArrayKey    ⇒ typeMapper[ArrayData[Long]]
    case KeyType.IntArrayKey     ⇒ typeMapper[ArrayData[Int]]
    case KeyType.FloatArrayKey   ⇒ typeMapper[ArrayData[Float]]
    case KeyType.DoubleArrayKey  ⇒ typeMapper[ArrayData[Double]]
    case KeyType.ByteMatrixKey   ⇒ typeMapper[MatrixData[Byte]]
    case KeyType.ShortMatrixKey  ⇒ typeMapper[MatrixData[Short]]
    case KeyType.LongMatrixKey   ⇒ typeMapper[MatrixData[Long]]
    case KeyType.IntMatrixKey    ⇒ typeMapper[MatrixData[Int]]
    case KeyType.FloatMatrixKey  ⇒ typeMapper[MatrixData[Float]]
    case KeyType.DoubleMatrixKey ⇒ typeMapper[MatrixData[Double]]
  }

  private def typeMapper[T: ClassTag: Format: ItemsFactory]: TypeMapper[PbParameter, Parameter[_]] = {
    TypeMapperSupport.parameterTypeMapper[T].asInstanceOf[TypeMapper[PbParameter, Parameter[_]]]
  }

}

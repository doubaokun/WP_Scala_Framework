/**
 * Generated by Scrooge
 *   version: 3.16.40
 *   rev: c3bbc209edcec136fff0d50ec6d0bf27d31c6aa2
 *   built at: 20140917-102732
 */
package com.whitepages.generated

import com.twitter.scrooge.{
  TFieldBlob, ThriftException, ThriftStruct, ThriftStructCodec3, ThriftStructFieldInfo, ThriftUtil}
import org.apache.thrift.protocol._
import org.apache.thrift.transport.{TMemoryBuffer, TTransport}
import java.nio.ByteBuffer
import java.util.Arrays
import scala.collection.immutable.{Map => immutable$Map}
import scala.collection.mutable.Builder
import scala.collection.mutable.{
  ArrayBuffer => mutable$ArrayBuffer, Buffer => mutable$Buffer,
  HashMap => mutable$HashMap, HashSet => mutable$HashSet}
import scala.collection.{Map, Set}

import com.persist.JsonOps._
import com.persist.json._
import com.persist.Exceptions.MappingException
import scala.util.Try


object Response extends ThriftStructCodec3[Response] {
  private val NoPassthroughFields = immutable$Map.empty[Short, TFieldBlob]
  val Struct = new TStruct("Response")
  val ZipField = new TField("zip", TType.STRING, 1)
  val ZipFieldManifest = implicitly[Manifest[String]]
  val CountyField = new TField("county", TType.STRING, 2)
  val CountyFieldManifest = implicitly[Manifest[String]]

  /**
   * Field information in declaration order.
   */
  lazy val fieldInfos: scala.List[ThriftStructFieldInfo] = scala.List[ThriftStructFieldInfo](
    new ThriftStructFieldInfo(
      ZipField,
      false,
      ZipFieldManifest,
      None,
      None,
      immutable$Map(
      ),
      immutable$Map(
      )
    ),
    new ThriftStructFieldInfo(
      CountyField,
      false,
      CountyFieldManifest,
      None,
      None,
      immutable$Map(
      ),
      immutable$Map(
      )
    )
  )

  lazy val structAnnotations: immutable$Map[String, String] =
    immutable$Map[String, String](
    )

  /**
   * Checks that all required fields are non-null.
   */
  def validate(_item: Response) {
  }

  def withoutPassthroughFields(original: Response): Response =
    new Immutable(
      zip =
        {
          val field = original.zip
          field
        },
      county =
        {
          val field = original.county
          field
        }
    )

  override def encode(_item: Response, _oproto: TProtocol) {
    _item.write(_oproto)
  }

  override def decode(_iprot: TProtocol): Response = {
    var zip: String = null
    var county: String = null
    var _passthroughFields: Builder[(Short, TFieldBlob), immutable$Map[Short, TFieldBlob]] = null
    var _done = false

    _iprot.readStructBegin()
    while (!_done) {
      val _field = _iprot.readFieldBegin()
      if (_field.`type` == TType.STOP) {
        _done = true
      } else {
        _field.id match {
          case 1 =>
            _field.`type` match {
              case TType.STRING => {
                zip = readZipValue(_iprot)
              }
              case _actualType =>
                val _expectedType = TType.STRING
            
                throw new TProtocolException(
                  "Received wrong type for field 'zip' (expected=%s, actual=%s).".format(
                    ttypeToHuman(_expectedType),
                    ttypeToHuman(_actualType)
                  )
                )
            }
          case 2 =>
            _field.`type` match {
              case TType.STRING => {
                county = readCountyValue(_iprot)
              }
              case _actualType =>
                val _expectedType = TType.STRING
            
                throw new TProtocolException(
                  "Received wrong type for field 'county' (expected=%s, actual=%s).".format(
                    ttypeToHuman(_expectedType),
                    ttypeToHuman(_actualType)
                  )
                )
            }
          case _ =>
            if (_passthroughFields == null)
              _passthroughFields = immutable$Map.newBuilder[Short, TFieldBlob]
            _passthroughFields += (_field.id -> TFieldBlob.read(_field, _iprot))
        }
        _iprot.readFieldEnd()
      }
    }
    _iprot.readStructEnd()

    new Immutable(
      zip,
      county,
      if (_passthroughFields == null)
        NoPassthroughFields
      else
        _passthroughFields.result()
    )
  }

  def apply(
    zip: String,
    county: String
  ): Response =
    new Immutable(
      zip,
      county
    )

  def unapply(_item: Response): Option[scala.Product2[String, String]] = Some(_item)

  implicit val jsonReadCodec = new ReadCodec[Response] {
    def read(json: Json) = {
      val map = ReadCodec.castOrThrow(json)
            val fieldValuezip = map.getOrElse("zip", throw new MappingException(s"Expected field zip on JsonObject $map"))
            val fieldValuecounty = map.getOrElse("county", throw new MappingException(s"Expected field county on JsonObject $map"))
      
      Response(      zip = Try(com.persist.json.read[String](fieldValuezip)).recover {
        case MappingException(msg, path) => throw MappingException(msg, s"zip/$path")
      }.get
      ,      county = Try(com.persist.json.read[String](fieldValuecounty)).recover {
        case MappingException(msg, path) => throw MappingException(msg, s"county/$path")
      }.get
      
      )
    }
   }
  implicit val jsonWriteCodec = new WriteCodec[Response] {
    def write(obj: Response) = {
      val fields = List(        
        Some("zip" -> com.persist.json.toJson(obj.zip)),        
        Some("county" -> com.persist.json.toJson(obj.county))
      )
      fields.flatten.toMap
    }
  }

  import com.twitter.scrooge.serialization._

  implicit val thriftCodec = new ThriftCodec[Response] {
    def decode(protocol: TProtocol): Response = Response.decode(protocol)
    def encode(obj: Response, protocol: TProtocol) { Response.encode(obj, protocol) }
  }

  private def readZipValue(_iprot: TProtocol): String = {
    _iprot.readString()
  }

  private def writeZipField(zip_item: String, _oprot: TProtocol) {
    _oprot.writeFieldBegin(ZipField)
    writeZipValue(zip_item, _oprot)
    _oprot.writeFieldEnd()
  }

  private def writeZipValue(zip_item: String, _oprot: TProtocol) {
    _oprot.writeString(zip_item)
  }

  private def readCountyValue(_iprot: TProtocol): String = {
    _iprot.readString()
  }

  private def writeCountyField(county_item: String, _oprot: TProtocol) {
    _oprot.writeFieldBegin(CountyField)
    writeCountyValue(county_item, _oprot)
    _oprot.writeFieldEnd()
  }

  private def writeCountyValue(county_item: String, _oprot: TProtocol) {
    _oprot.writeString(county_item)
  }



  private def ttypeToHuman(byte: Byte) = {
    // from https://github.com/apache/thrift/blob/master/lib/java/src/org/apache/thrift/protocol/TType.java
    byte match {
      case TType.STOP   => "STOP"
      case TType.VOID   => "VOID"
      case TType.BOOL   => "BOOL"
      case TType.BYTE   => "BYTE"
      case TType.DOUBLE => "DOUBLE"
      case TType.I16    => "I16"
      case TType.I32    => "I32"
      case TType.I64    => "I64"
      case TType.STRING => "STRING"
      case TType.STRUCT => "STRUCT"
      case TType.MAP    => "MAP"
      case TType.SET    => "SET"
      case TType.LIST   => "LIST"
      case TType.ENUM   => "ENUM"
      case _            => "UNKNOWN"
    }
  }

  object Immutable extends ThriftStructCodec3[Response] {
    override def encode(_item: Response, _oproto: TProtocol) { _item.write(_oproto) }
    override def decode(_iprot: TProtocol): Response = Response.decode(_iprot)

    import com.twitter.scrooge.serialization._

    implicit val thriftCodec = Response.thriftCodec
  }

  /**
   * The default read-only implementation of Response.  You typically should not need to
   * directly reference this class; instead, use the Response.apply method to construct
   * new instances.
   */
  class Immutable(
    val zip: String,
    val county: String,
    override val _passthroughFields: immutable$Map[Short, TFieldBlob]
  ) extends Response {
    def this(
      zip: String,
      county: String
    ) = this(
      zip,
      county,
      Map.empty
    )
  }

  /**
   * This Proxy trait allows you to extend the Response trait with additional state or
   * behavior and implement the read-only methods from Response using an underlying
   * instance.
   */
  trait Proxy extends Response {
    protected def _underlying_Response: Response
    override def zip: String = _underlying_Response.zip
    override def county: String = _underlying_Response.county
    override def _passthroughFields = _underlying_Response._passthroughFields
  }
}

trait Response
  extends ThriftStruct
  with scala.Product2[String, String]
  with java.io.Serializable
{
  import Response._

  def zip: String
  def county: String

  def _passthroughFields: immutable$Map[Short, TFieldBlob] = immutable$Map.empty

  def _1 = zip
  def _2 = county

  /**
   * Gets a field value encoded as a binary blob using TCompactProtocol.  If the specified field
   * is present in the passthrough map, that value is returend.  Otherwise, if the specified field
   * is known and not optional and set to None, then the field is serialized and returned.
   */
  def getFieldBlob(_fieldId: Short): Option[TFieldBlob] = {
    lazy val _buff = new TMemoryBuffer(32)
    lazy val _oprot = new TCompactProtocol(_buff)
    _passthroughFields.get(_fieldId) orElse {
      val _fieldOpt: Option[TField] =
        _fieldId match {
          case 1 =>
            if (zip ne null) {
              writeZipValue(zip, _oprot)
              Some(Response.ZipField)
            } else {
              None
            }
          case 2 =>
            if (county ne null) {
              writeCountyValue(county, _oprot)
              Some(Response.CountyField)
            } else {
              None
            }
          case _ => None
        }
      _fieldOpt match {
        case Some(_field) =>
          val _data = Arrays.copyOfRange(_buff.getArray, 0, _buff.length)
          Some(TFieldBlob(_field, _data))
        case None =>
          None
      }
    }
  }

  /**
   * Collects TCompactProtocol-encoded field values according to `getFieldBlob` into a map.
   */
  def getFieldBlobs(ids: TraversableOnce[Short]): immutable$Map[Short, TFieldBlob] =
    (ids flatMap { id => getFieldBlob(id) map { id -> _ } }).toMap

  /**
   * Sets a field using a TCompactProtocol-encoded binary blob.  If the field is a known
   * field, the blob is decoded and the field is set to the decoded value.  If the field
   * is unknown and passthrough fields are enabled, then the blob will be stored in
   * _passthroughFields.
   */
  def setField(_blob: TFieldBlob): Response = {
    var zip: String = this.zip
    var county: String = this.county
    var _passthroughFields = this._passthroughFields
    _blob.id match {
      case 1 =>
        zip = readZipValue(_blob.read)
      case 2 =>
        county = readCountyValue(_blob.read)
      case _ => _passthroughFields += (_blob.id -> _blob)
    }
    new Immutable(
      zip,
      county,
      _passthroughFields
    )
  }

  /**
   * If the specified field is optional, it is set to None.  Otherwise, if the field is
   * known, it is reverted to its default value; if the field is unknown, it is subtracked
   * from the passthroughFields map, if present.
   */
  def unsetField(_fieldId: Short): Response = {
    var zip: String = this.zip
    var county: String = this.county

    _fieldId match {
      case 1 =>
        zip = null
      case 2 =>
        county = null
      case _ =>
    }
    new Immutable(
      zip,
      county,
      _passthroughFields - _fieldId
    )
  }

  /**
   * If the specified field is optional, it is set to None.  Otherwise, if the field is
   * known, it is reverted to its default value; if the field is unknown, it is subtracked
   * from the passthroughFields map, if present.
   */
  def unsetZip: Response = unsetField(1)

  def unsetCounty: Response = unsetField(2)


  override def write(_oprot: TProtocol) {
    Response.validate(this)
    _oprot.writeStructBegin(Struct)
    if (zip ne null) writeZipField(zip, _oprot)
    if (county ne null) writeCountyField(county, _oprot)
    _passthroughFields.values foreach { _.write(_oprot) }
    _oprot.writeFieldStop()
    _oprot.writeStructEnd()
  }

  def copy(
    zip: String = this.zip,
    county: String = this.county,
    _passthroughFields: immutable$Map[Short, TFieldBlob] = this._passthroughFields
  ): Response =
    new Immutable(
      zip,
      county,
      _passthroughFields
    )

  override def canEqual(other: Any): Boolean = other.isInstanceOf[Response]

  override def equals(other: Any): Boolean =
    _root_.scala.runtime.ScalaRunTime._equals(this, other) &&
      _passthroughFields == other.asInstanceOf[Response]._passthroughFields

  override def hashCode: Int = _root_.scala.runtime.ScalaRunTime._hashCode(this)

  override def toString: String = _root_.scala.runtime.ScalaRunTime._toString(this)


  override def productArity: Int = 2

  override def productElement(n: Int): Any = n match {
    case 0 => this.zip
    case 1 => this.county
    case _ => throw new IndexOutOfBoundsException(n.toString)
  }

  override def productPrefix: String = "Response"
}
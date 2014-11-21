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


object HasList extends ThriftStructCodec3[HasList] {
  private val NoPassthroughFields = immutable$Map.empty[Short, TFieldBlob]
  val Struct = new TStruct("HasList")
  val HasLongsField = new TField("hasLongs", TType.LIST, 1)
  val HasLongsFieldManifest = implicitly[Manifest[Seq[HasLong]]]

  /**
   * Field information in declaration order.
   */
  lazy val fieldInfos: scala.List[ThriftStructFieldInfo] = scala.List[ThriftStructFieldInfo](
    new ThriftStructFieldInfo(
      HasLongsField,
      true,
      HasLongsFieldManifest,
      None,
      Some(implicitly[Manifest[HasLong]]),
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
  def validate(_item: HasList) {
  }

  def withoutPassthroughFields(original: HasList): HasList =
    new Immutable(
      hasLongs =
        {
          val field = original.hasLongs
          field.map { field =>
            field.map { field =>
              HasLong.withoutPassthroughFields(field)
            }
          }
        }
    )

  override def encode(_item: HasList, _oproto: TProtocol) {
    _item.write(_oproto)
  }

  override def decode(_iprot: TProtocol): HasList = {
    var hasLongs: Option[Seq[HasLong]] = None
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
              case TType.LIST => {
                hasLongs = Some(readHasLongsValue(_iprot))
              }
              case _actualType =>
                val _expectedType = TType.LIST
            
                throw new TProtocolException(
                  "Received wrong type for field 'hasLongs' (expected=%s, actual=%s).".format(
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
      hasLongs,
      if (_passthroughFields == null)
        NoPassthroughFields
      else
        _passthroughFields.result()
    )
  }

  def apply(
    hasLongs: Option[Seq[HasLong]] = None
  ): HasList =
    new Immutable(
      hasLongs
    )

  def unapply(_item: HasList): Option[Option[Seq[HasLong]]] = Some(_item.hasLongs)

  implicit val jsonReadCodec = new ReadCodec[HasList] {
    def read(json: Json) = {
      val map = ReadCodec.castOrThrow(json)
            val fieldValuehas_longs = map.getOrElse("has_longs", jnull)
      
      HasList(      hasLongs = Try(com.persist.json.read[Option[Seq[HasLong]]](fieldValuehas_longs)).recover {
        case MappingException(msg, path) => throw MappingException(msg, s"has_longs/$path")
      }.get
      
      )
    }
   }
  implicit val jsonWriteCodec = new WriteCodec[HasList] {
    def write(obj: HasList) = {
      val fields = List(        if(obj.hasLongs.isDefined) Some("has_longs" -> com.persist.json.toJson(obj.hasLongs.get)) else None
        
      )
      fields.flatten.toMap
    }
  }

  import com.twitter.scrooge.serialization._

  implicit val thriftCodec = new ThriftCodec[HasList] {
    def decode(protocol: TProtocol): HasList = HasList.decode(protocol)
    def encode(obj: HasList, protocol: TProtocol) { HasList.encode(obj, protocol) }
  }

  private def readHasLongsValue(_iprot: TProtocol): Seq[HasLong] = {
    val _list = _iprot.readListBegin()
    if (_list.size == 0) {
      _iprot.readListEnd()
      Nil
    } else {
      val _rv = new mutable$ArrayBuffer[HasLong](_list.size)
      var _i = 0
      while (_i < _list.size) {
        _rv += {
            HasLong.decode(_iprot)
  
        }
        _i += 1
      }
      _iprot.readListEnd()
      _rv
    }
  }

  private def writeHasLongsField(hasLongs_item: Seq[HasLong], _oprot: TProtocol) {
    _oprot.writeFieldBegin(HasLongsField)
    writeHasLongsValue(hasLongs_item, _oprot)
    _oprot.writeFieldEnd()
  }

  private def writeHasLongsValue(hasLongs_item: Seq[HasLong], _oprot: TProtocol) {
    _oprot.writeListBegin(new TList(TType.STRUCT, hasLongs_item.size))
    hasLongs_item.foreach { hasLongs_item_element =>
      hasLongs_item_element.write(_oprot)
    }
    _oprot.writeListEnd()
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

  object Immutable extends ThriftStructCodec3[HasList] {
    override def encode(_item: HasList, _oproto: TProtocol) { _item.write(_oproto) }
    override def decode(_iprot: TProtocol): HasList = HasList.decode(_iprot)

    import com.twitter.scrooge.serialization._

    implicit val thriftCodec = HasList.thriftCodec
  }

  /**
   * The default read-only implementation of HasList.  You typically should not need to
   * directly reference this class; instead, use the HasList.apply method to construct
   * new instances.
   */
  class Immutable(
    val hasLongs: Option[Seq[HasLong]],
    override val _passthroughFields: immutable$Map[Short, TFieldBlob]
  ) extends HasList {
    def this(
      hasLongs: Option[Seq[HasLong]] = None
    ) = this(
      hasLongs,
      Map.empty
    )
  }

  /**
   * This Proxy trait allows you to extend the HasList trait with additional state or
   * behavior and implement the read-only methods from HasList using an underlying
   * instance.
   */
  trait Proxy extends HasList {
    protected def _underlying_HasList: HasList
    override def hasLongs: Option[Seq[HasLong]] = _underlying_HasList.hasLongs
    override def _passthroughFields = _underlying_HasList._passthroughFields
  }
}

trait HasList
  extends ThriftStruct
  with scala.Product1[Option[Seq[HasLong]]]
  with java.io.Serializable
{
  import HasList._

  def hasLongs: Option[Seq[HasLong]]

  def _passthroughFields: immutable$Map[Short, TFieldBlob] = immutable$Map.empty

  def _1 = hasLongs

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
            if (hasLongs.isDefined) {
              writeHasLongsValue(hasLongs.get, _oprot)
              Some(HasList.HasLongsField)
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
  def setField(_blob: TFieldBlob): HasList = {
    var hasLongs: Option[Seq[HasLong]] = this.hasLongs
    var _passthroughFields = this._passthroughFields
    _blob.id match {
      case 1 =>
        hasLongs = Some(readHasLongsValue(_blob.read))
      case _ => _passthroughFields += (_blob.id -> _blob)
    }
    new Immutable(
      hasLongs,
      _passthroughFields
    )
  }

  /**
   * If the specified field is optional, it is set to None.  Otherwise, if the field is
   * known, it is reverted to its default value; if the field is unknown, it is subtracked
   * from the passthroughFields map, if present.
   */
  def unsetField(_fieldId: Short): HasList = {
    var hasLongs: Option[Seq[HasLong]] = this.hasLongs

    _fieldId match {
      case 1 =>
        hasLongs = None
      case _ =>
    }
    new Immutable(
      hasLongs,
      _passthroughFields - _fieldId
    )
  }

  /**
   * If the specified field is optional, it is set to None.  Otherwise, if the field is
   * known, it is reverted to its default value; if the field is unknown, it is subtracked
   * from the passthroughFields map, if present.
   */
  def unsetHasLongs: HasList = unsetField(1)


  override def write(_oprot: TProtocol) {
    HasList.validate(this)
    _oprot.writeStructBegin(Struct)
    if (hasLongs.isDefined) writeHasLongsField(hasLongs.get, _oprot)
    _passthroughFields.values foreach { _.write(_oprot) }
    _oprot.writeFieldStop()
    _oprot.writeStructEnd()
  }

  def copy(
    hasLongs: Option[Seq[HasLong]] = this.hasLongs,
    _passthroughFields: immutable$Map[Short, TFieldBlob] = this._passthroughFields
  ): HasList =
    new Immutable(
      hasLongs,
      _passthroughFields
    )

  override def canEqual(other: Any): Boolean = other.isInstanceOf[HasList]

  override def equals(other: Any): Boolean =
    _root_.scala.runtime.ScalaRunTime._equals(this, other) &&
      _passthroughFields == other.asInstanceOf[HasList]._passthroughFields

  override def hashCode: Int = _root_.scala.runtime.ScalaRunTime._hashCode(this)

  override def toString: String = _root_.scala.runtime.ScalaRunTime._toString(this)


  override def productArity: Int = 1

  override def productElement(n: Int): Any = n match {
    case 0 => this.hasLongs
    case _ => throw new IndexOutOfBoundsException(n.toString)
  }

  override def productPrefix: String = "HasList"
}
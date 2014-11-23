package com.whitepages.framework.util

/*

import com.twitter.scrooge.serialization.ThriftCodec
import org.apache.thrift.protocol.{TProtocol, TMessage, TCompactProtocol, TBinaryProtocol}
import org.apache.thrift.transport.{TTransport, TMemoryBuffer, TMemoryInputTransport}
import com.twitter.scrooge.ThriftStruct
import spray.http.MediaTypes._
import spray.http.MediaType
import scala.language.existentials

import com.twitter.scrooge.Info

/**
 * This object contains methods for working with Thrift data.
 * It should be (mostly) replaced after the Scrooge rework.
 */
object Thrift {

  /**
   * The HTTP media type for Thrift (application/x-thrift).
   */
  val thriftType = register(MediaType.custom(
    mainType = "application",
    subType = "x-thrift",
    binary = true
  ))


  /**
   *  Common trait for thrift protocols.
   */
  trait ThriftProtocol

  /** ThriftBinaryProtocol is passed as the protocol argument to
    * serializeThrift or deserializeThrift to indicate that the
    * binary protocol should be used.
    */
  case object ThriftBinaryProtocol extends ThriftProtocol

  /** ThriftCompactProtocol is passed as the protocol argument to
    * serializeThrift or deserializeThrift to indicate that the
    * compact protocol should be used.
    */
  case object ThriftCompactProtocol extends ThriftProtocol

  /**
   * @param inBytes  input data.
   * @param protocol the protocol that in was serialized with.
   */
  def deserializeThrift[T <: ThriftStruct](inBytes: Array[Byte], protocol: ThriftProtocol)(implicit codec: ThriftCodec[T]): (T, TMessage) = {
    val transport = new TMemoryInputTransport(inBytes)
    val iprot = getThriftProtocol(protocol, transport)
    val msg = iprot.readMessageBegin()
    iprot.readMessageEnd()
    (com.twitter.scrooge.serialization.decode(iprot), msg)
  }

  def deserializeThriftWithoutMessage[T <: ThriftStruct](inBytes: Array[Byte], protocol: ThriftProtocol)(implicit codec: ThriftCodec[T]): T = {
    val transport = new TMemoryInputTransport(inBytes)
    val iprot = getThriftProtocol(protocol, transport)
    com.twitter.scrooge.serialization.decode(iprot)
  }

  def deserializeThriftIn(inBytes: Array[Byte], protocol: ThriftProtocol, infos: Map[String, Info]): (ThriftStruct, TMessage) = {
    val transport = new TMemoryInputTransport(inBytes)
    val iprot = getThriftProtocol(protocol, transport)
    val msg = iprot.readMessageBegin()
    val cmd = msg.name
    val cmdCamelCase = snakeToCamel(cmd)
    val info = infos(cmdCamelCase)
    iprot.readMessageEnd()
    (info.in.companion.decode(iprot), msg)
  }

  def deserializeThriftOut(inBytes: Array[Byte], protocol: ThriftProtocol, infos: Map[String, Info]): (ThriftStruct, TMessage) = {
    val transport = new TMemoryInputTransport(inBytes)
    val iprot = getThriftProtocol(protocol, transport)
    val msg = iprot.readMessageBegin()
    val cmd = msg.name
    val cmdCamelCase = snakeToCamel(cmd)
    val info = infos(cmdCamelCase)
    iprot.readMessageEnd()
    (info.out.companion.decode(iprot), msg)
  }

  /** Convert a string to snake case.
    *
    * To convert to snake case, this converter inserts an underscore before every
    * capital letter, unless that letter is at the start or is preceded by another
    * capital letter, and then all capitals are converted to lower case.
    *
    * Examples:
    *
    * oneTwo -> one_two
    * One    -> _one
    * showUS -> show_us
    * NBC    -> _nbc
    *
    * Note that camelToSnake and snakeToCamel are not always reversible. For example,
    * showUS converts to show_us, which would back convert to showUs.
    *
    * @param in Input string (typically in camal case)
    * @return Output string in snake case
    */
  def camelToSnake(in: String) = {
    val camelToSnakeRegex = "(\\A|[^A-Z_])([A-Z])".r
    camelToSnakeRegex.replaceAllIn(in, "$1_$2").toLowerCase
  }

  /** Convert a string to camel case.
    *
    * The output camel case has a capital letter at the start of each word (i.e.
    * after each underscore), except for the first word (unless that word starts with
    * an underscore).
    *
    * Examples:
    *
    * one_two  -> oneTwo
    * _one_two -> OneTwo
    * show_us  -> showUs
    * nbc      -> nbc
    *
    * Note that camelToSnake and snakeToCamel are not always reversible.
    *
    * @param in Input string (typically in snake case)
    * @return Output string in camel case
    */
  def snakeToCamel(in: String) = {
    val parts = in.split("_")
    parts.head + parts.tail.map(_.capitalize).mkString("")
  }

  // bytes = serializeThift(obj)

  /**
   * Serializes thrift. Typical usage pattern (outgoing server responses) is
   *  {{{
   *  val outBytes: Array[Byte] = serializeThrift(inObject, ThriftBinaryProtocol, msg = msg)
   *   }}}
   *
   * @param inObject  the Scrooge generated Scala object to be serialized.
   * @param protocol the protocol used to serialize.
   * @param msg the Apache thrift TMessage.
   * @return the serialized data.
   */
  def serializeThrift(inObject: ThriftStruct, protocol: ThriftProtocol, msg: TMessage = null):Array[Byte] = {
    val otransport = new TMemoryBuffer(5)
    val oprot = getThriftProtocol(protocol, otransport)
    if (msg != null) {
      oprot.writeMessageBegin(msg)
      oprot.writeMessageEnd()
    }
    inObject.write(oprot)
    val out = otransport.getArray().take(otransport.length())
    out
  }

  private def getThriftProtocol(protocol: ThriftProtocol, transport: TTransport) = protocol match {
    case ThriftBinaryProtocol => new TBinaryProtocol(transport)
    case ThriftCompactProtocol => new TCompactProtocol(transport)
    case p => throw new Exception("Invalid thrift protocol " + p)
  }

  /**
   * This method reflectively looks up information about a Scrooge generated Thrift objects.
   * @param path  the package containing the Scrooge generated Scala code for Thrift.
   * @param name  the name of the Thrift service that defines the set of Thrift commands.
   * @return an info data structure that is used for Thrift serialization/de-serialization and conversion between Thrift
   *         and Json.
   */
  def info(path: String, name: String): Map[String, Info] = {
    if (path == "" || name == "") Map.empty
    else {
      import scala.reflect.runtime.universe
      val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)
      val module = runtimeMirror.staticModule(s"$path.$name")
      val obj = runtimeMirror.reflectModule(module).instance

      import scala.language.reflectiveCalls
      val serviceCompanionObject = obj.asInstanceOf[{def map: scala.collection.Map[String, Info]}]
      // TODO: Maybe supply an immutable map from Scrooge instead of having to do a useless conversion
      serviceCompanionObject.map.toMap
    }
  }
}
*/


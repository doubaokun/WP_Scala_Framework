package com.whitepages.framework.util

import java.nio.file._
import scala.concurrent.{Promise, Future}
import java.nio.charset.Charset
import java.nio.channels.{CompletionHandler, AsynchronousFileChannel}
import java.nio.ByteBuffer
import java.nio.file.StandardOpenOption.{CREATE, WRITE}

/**
 * This object provides some basic async file io.
 */
object FileIO {
  private[this] val charset = Charset.forName("UTF-8")

  /**
   * Reads a file into a string.
   * @param fname the file name
   * @return  a future that will contain a string holding all lines in the file.
   */
  def read(fname: String): Future[String] = {
    val p = Promise[String]
    val buff = new Array[Byte](1000)
    val path = Paths.get(fname)
    val channel = AsynchronousFileChannel.open(path)
    var buffer: ByteBuffer = null
    def readNext(sb: StringBuilder) {
      buffer = ByteBuffer.wrap(buff)
      channel.read(buffer, sb.length, sb, handler)
    }
    def handler = new CompletionHandler[Integer, StringBuilder] {
      def completed(bytesTransferred: Integer, sb: StringBuilder) = {
        if (bytesTransferred <= 0) {
          // Done
          p.success(sb.toString())
          channel.close()
        } else {
          // More to read
          val s = new String(buffer.array().take(bytesTransferred), "UTF-8")
          readNext(sb.append(s))
        }
      }

      def failed(ex: Throwable, sb: StringBuilder) = {
        p.failure(ex)
        channel.close()
      }
    }
    readNext(new StringBuilder())
    p.future
  }

  /**
   * Writes a multi-line string to a file.
   * @param fname the name of the file.
   * @param data  the string to be written.
   * @return  a future that completes when the write is complete.
   */
  def write(fname: String, data: String): Future[Unit] = {
    val p = Promise[Unit]
    val buffer = ByteBuffer.wrap(data.getBytes("UTF-8"))
    val path = Paths.get(fname)
    val channel = AsynchronousFileChannel.open(path,
      StandardOpenOption.CREATE,
      StandardOpenOption.WRITE,
      StandardOpenOption.TRUNCATE_EXISTING)
    val size = data.size
    def writeNext(totalTransferred: Long) {
      channel.write(buffer.slice(), totalTransferred, 0L, handler)
    }
    def handler = new CompletionHandler[Integer, Long] {
      def completed(bytesTransferred: Integer, totalTransferred: Long) = {
        val totalTransferred1 = totalTransferred + bytesTransferred
        if (totalTransferred1 == size) {
          // Done
          p.success(())
          channel.close()
        } else {
          // Write more
          writeNext(totalTransferred1)
        }
      }

      def failed(ex: Throwable, position: Long) = {
        p.failure(ex)
        channel.close()
      }
    }
    writeNext(0L)
    p.future
  }

}

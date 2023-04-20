package zio
package pgcopy

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import zio.test.Assertion.*
import zio.test.*

import Codec.*

object CodecSpec extends ZIOSpecDefault:

  def spec: Spec[Environment & TestEnvironment, Any] =
    suite("codecs") {
      suite("numeric") {
        test("int2") {
          given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
          buf.clear; int2(-1); assertTrue(-1 == int2())
          buf.clear; int2(0); assertTrue(0 == int2())
          buf.clear; int2(1); assertTrue(1 == int2())
          buf.clear; int2(Short.MinValue); assertTrue(Short.MinValue == int2())
          buf.clear; int2(Short.MaxValue); assertTrue(Short.MaxValue == int2())
          buf.clear; _int2(Array(Short.MinValue, -1, 0, 1, Short.MaxValue));
          assertTrue(Array(Short.MinValue, -1, 0, 1, Short.MaxValue).sameElements(_int2()))
        }
          + test("int4") {
            given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
            buf.clear; int4(-1); assertTrue(-1 == int4())
            buf.clear; int4(0); assertTrue(0 == int4())
            buf.clear; int4(1); assertTrue(1 == int4())
            buf.clear; int4(Int.MinValue); assertTrue(Int.MinValue == int4())
            buf.clear; int4(Int.MaxValue); assertTrue(Int.MaxValue == int4())
          }
          + test("int8") {
            given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
            buf.clear; int8(-1); assertTrue(-1 == int8())
            buf.clear; int8(0); assertTrue(0 == int8())
            buf.clear; int8(1); assertTrue(1 == int8())
            buf.clear; int8(Long.MinValue); assertTrue(Long.MinValue == int8())
            buf.clear; int8(Long.MaxValue); assertTrue(Long.MaxValue == int8())
          }
          + test("float4") {
            given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
            buf.clear; float4(-1); assertTrue(-1 == float4())
            buf.clear; float4(0); assertTrue(0 == float4())
            buf.clear; float4(1); assertTrue(1 == float4())
            buf.clear; float4(math.Pi.toFloat); assertTrue(math.Pi.toFloat == float4())
            buf.clear; float4(Float.MinValue); assertTrue(Float.MinValue == float4())
            buf.clear; float4(Float.MaxValue); assertTrue(Float.MaxValue == float4())
            buf.clear; _float4(Array(Float.MinValue, -1, 0, 1, math.Pi.toFloat, Float.MaxValue));
            assertTrue(Array(Float.MinValue, -1, 0, 1, math.Pi.toFloat, Float.MaxValue).sameElements(_float4()))
          }
          + test("float8") {
            given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
            buf.clear; float8(-1); assertTrue(-1 == float8())
            buf.clear; float8(0); assertTrue(0 == float8())
            buf.clear; float8(1); assertTrue(1 == float8())
            buf.clear; float8(math.Pi); assertTrue(math.Pi == float8())
            buf.clear; float8(Double.MinValue); assertTrue(Double.MinValue == float8())
            buf.clear; float8(Double.MaxValue); assertTrue(Double.MaxValue == float8())
          }
          + test("numeric") {
            given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](32 * 1024))
            buf.clear; numeric(-1); assertTrue(-1 == numeric())
            buf.clear; numeric(0); assertTrue(0 == numeric())
            buf.clear; numeric(1); assertTrue(1 == numeric())
            buf.clear; numeric(math.Pi); assertTrue(math.Pi == numeric())
            buf.clear; numeric(BigDecimal("-3.14e-21")); assertTrue(BigDecimal("-3.14e-21") == numeric())
            buf.clear; numeric(BigDecimal("-3.14e210")); assertTrue(BigDecimal("-3.14e210") == numeric())
            buf.clear; numeric(BigDecimal("827349823749823749283749283749287928234729847928347293847293847923847239847.984384279487"));
            assertTrue(BigDecimal("827349823749823749283749283749287928234729847928347293847293847923847239847.984384279487") == numeric())
            buf.clear;
            val n = BigDecimal(
              "82734982374982374928374928374928700000092823472984792834729384729003847923847239847827349823749823749280000000037492837492879282347298470000928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847827349823749823749283749283749287928234729847928347293847293847923847239847.123"
            ); numeric(n); assertTrue(n == numeric())
            buf.clear; numeric(BigDecimal(Float.MinPositiveValue)); assertTrue(BigDecimal(Float.MinPositiveValue) == numeric())
            buf.clear; numeric(Int.MinValue); assertTrue(Int.MinValue == numeric())
            buf.clear; numeric(Int.MaxValue); assertTrue(Int.MaxValue == numeric())
            buf.clear; numeric(Long.MinValue); assertTrue(Long.MinValue == numeric())
            buf.clear; numeric(Long.MaxValue); assertTrue(Long.MaxValue - 1 == numeric() - 1)
            buf.clear; numeric(Long.MaxValue); assertTrue(Long.MaxValue == numeric())
            buf.clear; numeric(BigDecimal("3.14E38")); assertTrue(BigDecimal("3.14E38") == numeric())
            buf.clear; numeric(BigDecimal(Float.MinValue)); assertTrue(BigDecimal(Float.MinValue) == numeric())
            buf.clear; numeric(BigDecimal(Float.MaxValue)); assertTrue(BigDecimal(Float.MaxValue) == numeric())
            buf.clear; numeric(BigDecimal(Float.MinPositiveValue)); assertTrue(BigDecimal(Float.MinPositiveValue) == numeric())
            buf.clear; numeric(BigDecimal(Double.MinValue)); assertTrue(BigDecimal(Double.MinValue) == numeric())
            buf.clear; numeric(BigDecimal(Double.MaxValue)); assertTrue(BigDecimal(Double.MaxValue) == numeric())
            buf.clear; numeric(BigDecimal(Double.MinPositiveValue)); assertTrue(BigDecimal(Double.MinPositiveValue) == numeric())
          }
      } + suite("text") {
        test("text") {
          given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
          buf.clear; text(""); assertTrue("" == text())
          buf.clear; text("A"); assertTrue("A" == text())
          buf.clear; text("äöüÄÖÜß@€"); assertTrue("äöüÄÖÜß@€" == text())
          buf.clear; text("史密斯是王明的朋友。"); assertTrue("史密斯是王明的朋友。" == text())
        } +
          test("name") {
            given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
            buf.clear; name(""); assertTrue("" == name())
            buf.clear; name("1234567890123456789012345678901234567890123456789012345678901234567890");
            assertTrue("123456789012345678901234567890123456789012345678901234567890123" == name())
            buf.clear; text("1234567890123456789012345678901234567890123456789012345678901234567890");
            assertTrue("123456789012345678901234567890123456789012345678901234567890123" == name())
          } +
          test("char") {
            given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
            buf.clear; char(0.toChar); assertTrue(0.toChar == char())
            buf.clear; char('A'); assertTrue('A' == char())
            buf.clear; char('@'); assertTrue('@' == char())
          }
      } + suite("others") {
        test("bool") {
          given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
          buf.clear; bool(false); assertTrue(false == bool())
          buf.clear; bool(true); assertTrue(true == bool())
          buf.clear; _bool(Array(true, true, false, false, true)); assertTrue(Array(true, true, false, false, true).sameElements(_bool()))
        } +
          test("bytea") {
            given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
            buf.clear; var bytes = Array.emptyByteArray; bytea(bytes); assertTrue(Array.emptyByteArray.sameElements(bytea()))
            buf.clear; bytes = Array(0); bytea(bytes); assertTrue(Array(0).sameElements(bytea()))
            buf.clear; bytes = "lkasjdflkajsdflkjasdöfl kjasldökfjasldkfjasldkfjalskdjflöasdkjföalskdjf".getBytes("UTF-8").nn
            bytea(bytes); assertTrue(bytes.sameElements(bytea()))
          } +
          test("uuid") {
            import Util.Uuid
            import java.util.UUID
            given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
            buf.clear; var u = Uuid(UUID.randomUUID.nn); uuid(u); assertTrue(u == uuid())
            buf.clear; u = Uuid(java.util.UUID.randomUUID.nn); uuid(u); assertTrue(u == uuid())
            buf.clear; u = Uuid(UUID.fromString("00000000-0000-0000-0000-000000000000")); uuid(u); assertTrue(u == uuid())
          }
      } + suite("timestamps") {
        test("interval") {
          import Util.Interval
          given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
          buf.clear; var i = Interval(1970, 1, 1, 0, 0, 0); interval(i); assertTrue(i == interval())
          buf.clear; i = Interval(2023, 4, 21, 14, 59, 59.099); interval(i); assertTrue(i == interval())
        } +
          test("timestamptz") {
            import java.time.OffsetDateTime
            import java.time.LocalDate
            import java.time.LocalTime
            import java.time.ZoneOffset.UTC
            given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
            buf.clear; var t = OffsetDateTime.now(UTC); timestamptz(t); assertTrue(t == timestamptz())
            buf.clear; t = OffsetDateTime.of(LocalDate.of(1970, 1, 1), LocalTime.of(0, 0), UTC); timestamptz(t);
            assertTrue(t == timestamptz())
            buf.clear; t = OffsetDateTime.of(LocalDate.of(1945, 5, 8), LocalTime.of(10, 59), UTC); timestamptz(t);
            assertTrue(t == timestamptz())
          } +
          test("date") {
            import java.time.LocalDate
            given buf: ByteBuf = Unpooled.wrappedBuffer(Array.ofDim[Byte](2048))
            buf.clear; var d = LocalDate.of(1945, 5, 8); date(d); assertTrue(d == date())
            buf.clear; d = LocalDate.of(1970, 1, 1); date(d); assertTrue(d == date())
            buf.clear; d = LocalDate.now; date(d); assertTrue(d == date())
            buf.clear; d = LocalDate.EPOCH; date(d); assertTrue(d == date())
            buf.clear; d = LocalDate.of(-4713, 1, 1); date(d); assertTrue(d == date())
            buf.clear; d = LocalDate.of(5874897, 12, 31); date(d); assertTrue(d == date())
          }
      }
    }

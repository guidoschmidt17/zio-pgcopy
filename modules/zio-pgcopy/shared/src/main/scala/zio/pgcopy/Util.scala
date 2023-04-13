package zio
package pgcopy

import io.netty.buffer.ByteBuf
import zio.*

import java.math.BigInteger
import java.util.UUID
import scala.collection.mutable.ListBuffer

object Util:

  case class Interval(years: Int, months: Int, days: Int, hours: Int, minutes: Int, seconds: Double)

  class Uuid(val uuid: UUID) extends AnyVal:

    override def toString = uuid.toString

    inline def write(buf: ByteBuf) =
      buf.writeLong(uuid.getMostSignificantBits)
      buf.writeLong(uuid.getLeastSignificantBits)

  object Uuid:

    inline def read(buf: ByteBuf) = Uuid(new UUID(buf.readLong, buf.readLong))

    inline def nextUuid: UIO[Uuid] = Random.nextUUID.flatMap(uuid => ZIO.succeed(Uuid(uuid)))

    inline given Conversion[Uuid, UUID] = _.uuid

    inline given Conversion[UUID, Uuid] = Uuid(_)

  inline final def ceilPower2(i: Int): Int =
    var x = i - 1
    x |= x >> 1
    x |= x >> 2
    x |= x >> 4
    x |= x >> 8
    x |= x >> 16
    x + 1

  private[pgcopy] case class NumericComponents(weight: Int, sign: Int, scale: Int, digits: ListBuffer[Int]):
    final val length = digits.length

  private[pgcopy] object NumericComponents:
    final val BigInt10000 = BigInteger.valueOf(10000)
    final val IntPowerOfTen = Range(0, 6).map(math.pow(10, _)).map(_.toInt)
    final val BigIntPowerOfTen = Range(0, 6).map(BigInteger.TEN.pow(_).nn)
    inline def powerOfTen(e: Int) = if e < BigIntPowerOfTen.length then BigIntPowerOfTen(e) else BigInteger.TEN.pow(e)
    inline final val POS = 0x0000
    inline final val NEG = 0x4000
    def apply(weight: Int, sign: Int, scale: Int, digits: Seq[Short]): BigDecimal =
      if digits.length == 0 then BigDecimal(0)
      else
        var unscaledint = digits(0).toLong
        var unscaled: BigInteger | Null = null
        if weight < 0 then
          var effectivescale = scale
          if weight + 1 < 0 then effectivescale += 4 * (weight + 1)
          Range(1, digits.length).foreach(i =>
            if unscaledint == 0 then
              effectivescale -= 4
              unscaledint = digits(i)
          )

          ???
        else if scale == 0 then
          Range(1, digits.length).foreach(i =>
            if i == 4 then unscaled = BigInteger.valueOf(unscaledint)
            var d: Int = digits(i)
            if unscaled == null then
              unscaledint *= 10000
              unscaledint += d
            else
              unscaled = unscaled.multiply(BigInt10000)
              if d != 0 then unscaled = unscaled.add(BigInteger.valueOf(d))
          )
          if unscaled == null then unscaled = BigInteger.valueOf(unscaledint)
          if sign == NEG then unscaled = unscaled.negate
          val bdscale = (digits.length - (weight + 1)) * 4
          BigDecimal(if bdscale == 0 then new java.math.BigDecimal(unscaled) else new java.math.BigDecimal(unscaled, bdscale).setScale(0))
        else
          var effectiveweight = weight
          var effectivescale = scale
          Range(1, digits.length).foreach(i =>
            if i == 4 then unscaled = BigInteger.valueOf(unscaledint)
            var d: Int = digits(i)
            if effectiveweight > 0 then
              effectiveweight -= 1
              if unscaled == null then unscaledint *= 10000 else unscaled = unscaled.multiply(BigInt10000)
            else if effectivescale >= 4 then
              effectivescale -= 4
              if unscaled == null then unscaledint *= 10000 else unscaled = unscaled.multiply(BigInt10000)
            else
              if unscaled == null then unscaledint *= IntPowerOfTen(effectivescale)
              else unscaled = unscaled.multiply(powerOfTen(effectivescale))
              d = d / IntPowerOfTen(4 - effectivescale)
              effectivescale = 0
            if unscaled == null then unscaledint += d else if d != 0 then unscaled = unscaled.add(BigInteger.valueOf(d))
          )
          if unscaled == null then unscaled = BigInteger.valueOf(unscaledint)
          if effectiveweight > 0 then unscaled = unscaled.multiply(powerOfTen(4 * effectiveweight))
          if effectivescale > 0 then unscaled = unscaled.multiply(powerOfTen(effectivescale))
          if sign == NEG then unscaled = unscaled.negate
          BigDecimal(new java.math.BigDecimal(unscaled, scale))
    def apply(a: BigDecimal): NumericComponents =
      var unscaled: BigInteger = a.underlying.unscaledValue
      val scale = a.scale
      val weight = if scale > 0 then (scale + 3) / 4 else 0
      val sign = if unscaled.signum == -1 then NEG else POS
      unscaled = if unscaled.signum == -1 then unscaled.negate else unscaled
      val digits: ListBuffer[Int] = ListBuffer()
      if scale > 0 then
        val remainder = scale % 4
        if remainder != 0 then
          val result = unscaled.divideAndRemainder(BigIntPowerOfTen(remainder))
          unscaled = result(0)
          digits.insert(0, result(1).intValue * IntPowerOfTen(4 - remainder))
        while unscaled != BigInteger.ZERO do
          val result = unscaled.divideAndRemainder(BigInt10000)
          unscaled = result(0)
          digits.insert(0, result(1).intValue)
        NumericComponents(weight, sign, math.max(0, scale), digits)
      else
        unscaled = unscaled.multiply(BigInteger.TEN.pow(-scale))
        while unscaled != BigInteger.ZERO do
          val result = unscaled.divideAndRemainder(BigInt10000)
          unscaled = result(0)
          digits.insert(0, result(1).intValue)
        NumericComponents(0, 0, scale, digits)

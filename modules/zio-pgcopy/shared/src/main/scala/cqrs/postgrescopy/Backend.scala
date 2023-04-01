package postgrescopy

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil

import scala.annotation.switch

private[postgrescopy] trait BackendMessage

private[postgrescopy] object BackendMessage:
  def apply()(using buf: ByteBuf): BackendMessage =
    val tag = buf.readByte
    val len = buf.readInt - 4
    (tag: @switch) match
      case CopyData.Tag if len == 2   => CopyDataFooter()
      case CopyData.Tag               => CopyData()
      case DataRow.Tag                => DataRow()
      case AuthenticationResponse.Tag => AuthenticationResponse()
      case ParameterStatus.Tag        => ParameterStatus()
      case BackendKeyData.Tag         => BackendKeyData()
      case ReadyForQuery.Tag          => ReadyForQuery()
      case CopyOutResponse.Tag        => CopyOutResponse()
      case CopyInResponse.Tag         => CopyInResponse()
      case CopyDone.Tag               => CopyDone()
      case NoData.Tag                 => NoData()
      case CommandComplete.Tag        => CommandComplete()
      case EmptyQueryRespponse.Tag    => EmptyQueryRespponse()
      case ParseComplete.Tag          => ParseComplete()
      case BindComplete.Tag           => BindComplete()
      case CloseComplete.Tag          => CloseComplete()
      case ParameterDescription.Tag   => ParameterDescription()
      case RowDescription.Tag         => RowDescription()
      case ErrorResponse.Tag          => ErrorResponse()
      case _                          => UnhandledMessage(tag, len)

  case class UnhandledMessage(tag: Byte, len: Int)(using buf: ByteBuf) extends BackendMessage:
    override def toString = s"UnhandledMessage(${tag.toChar} $len ${ByteBufUtil.hexDump(buf)})"

  trait AuthenticationResponse extends BackendMessage
  object AuthenticationResponse extends Decoder[AuthenticationResponse]:
    inline final val Tag = 'R'
    def apply()(using buf: ByteBuf) =
      (buf.readInt: @switch) match
        case AuthenticationOk.Tag                => AuthenticationOk()
        case AuthenticationClearTextPassword.Tag => AuthenticationClearTextPassword()
        case AuthenticationMD5Password.Tag       => AuthenticationMD5Password()
        case AuthenticationSASL.Tag              => AuthenticationSASL()
        case AuthenticationSASLContinue.Tag      => AuthenticationSASLContinue()
        case AuthenticationSASLFinal.Tag         => AuthenticationSASLFinal()

  case object AuthenticationOk extends AuthenticationResponse, Decoder[AuthenticationOk.type]:
    inline final val Tag = 0
    def apply()(using ByteBuf) = this

  case object AuthenticationClearTextPassword extends AuthenticationResponse, Decoder[AuthenticationClearTextPassword.type]:
    inline final val Tag = 3
    def apply()(using ByteBuf) = this

  case class AuthenticationMD5Password(salt: Array[Byte]) extends AuthenticationResponse
  object AuthenticationMD5Password extends Decoder[AuthenticationMD5Password]:
    inline final val Tag = 5
    def apply()(using buf: ByteBuf) = AuthenticationMD5Password(buf.readByteArray(4))

  case class AuthenticationSASL(mechanisms: Seq[String]) extends AuthenticationResponse
  object AuthenticationSASL extends Decoder[AuthenticationSASL]:
    inline final val Tag = 10
    def apply()(using buf: ByteBuf) =
      var mechanisms = Seq.empty[String]
      var more = true
      while more do
        buf.readUtf8z match
          case "" => more = false
          case s  => mechanisms = mechanisms :+ s
      AuthenticationSASL(mechanisms)

  case class AuthenticationSASLContinue(data: Array[Byte]) extends AuthenticationResponse
  object AuthenticationSASLContinue extends Decoder[AuthenticationSASLContinue]:
    inline final val Tag = 11
    def apply()(using buf: ByteBuf) =
      new AuthenticationSASLContinue(buf.readRemaining)

  case class AuthenticationSASLFinal(data: Array[Byte]) extends AuthenticationResponse
  object AuthenticationSASLFinal extends Decoder[AuthenticationSASLFinal]:
    inline final val Tag = 12
    def apply()(using buf: ByteBuf) =
      new AuthenticationSASLFinal(buf.readRemaining)

  case class ParameterStatus(name: String, value: String) extends BackendMessage
  object ParameterStatus extends Decoder[ParameterStatus]:
    inline final val Tag = 'S'
    def apply()(using buf: ByteBuf) = ParameterStatus(buf.readUtf8z, buf.readUtf8z)

  case class BackendKeyData(pid: Int, secret: Int) extends BackendMessage
  object BackendKeyData extends Decoder[BackendKeyData]:
    inline final val Tag = 'K'
    def apply()(using buf: ByteBuf) = BackendKeyData(buf.readInt, buf.readInt)

  case class ReadyForQuery(indicator: Char) extends BackendMessage
  object ReadyForQuery extends Decoder[ReadyForQuery]:
    inline final val Tag = 'Z'
    def apply()(using buf: ByteBuf) = ReadyForQuery(buf.readByte.toChar)

  case class CopyOutResponse(format: Byte, columns: Short, columnformats: Seq[Short]) extends BackendMessage
  object CopyOutResponse extends Decoder[CopyOutResponse]:
    inline final val Tag = 'H'
    def apply()(using buf: ByteBuf) =
      val format = buf.readByte
      val columns = buf.readShort
      val columnformats = Array.ofDim[Short](columns)
      Range(0, columns).foreach(i => columnformats.update(i, buf.readShort))
      CopyOutResponse(format, columns, columnformats.toSeq)

  case class CopyInResponse(format: Byte, columns: Short, columnformats: Seq[Short]) extends BackendMessage
  object CopyInResponse extends Decoder[CopyInResponse]:
    inline final val Tag = 'G'
    def apply()(using buf: ByteBuf) =
      val format = buf.readByte
      val columns = buf.readShort
      val columnformats = Array.ofDim[Short](columns)
      Range(0, columns).foreach(i => columnformats.update(i, buf.readShort))
      CopyInResponse(format, columns, columnformats.toSeq)

  case object CopyDataFooter extends BackendMessage, Decoder[CopyDataFooter.type]:
    def apply()(using ByteBuf) = this

  case class CopyData(fields: Short, data: ByteBuf)(using ByteBuf) extends BackendMessage
  object CopyData extends Decoder[CopyData]:
    inline final val Tag = 'd'
    def apply()(using buf: ByteBuf): CopyData =
      if buf.testUtf8("PGCOPY") then buf.readIgnore(19)
      CopyData(buf.readShort, buf.retain(1))

  case object CopyDone extends BackendMessage, Decoder[CopyDone.type]:
    inline final val Tag = 'c'
    def apply()(using ByteBuf) = this

  case class CommandComplete(completion: String) extends BackendMessage
  object CommandComplete extends Decoder[CommandComplete]:
    inline final val Tag = 'C'
    def apply()(using buf: ByteBuf) = CommandComplete(buf.readUtf8z)

  case class DataRow(columns: Seq[Array[Byte]]) extends BackendMessage
  object DataRow extends Decoder[DataRow]:
    inline final val Tag = 'D'
    def apply()(using buf: ByteBuf) =
      val columns = buf.readShort
      DataRow(Range(0, buf.readShort).map(_ => (buf.readByteArray(buf.readInt))))

  case object EmptyQueryRespponse extends BackendMessage:
    inline final val Tag = 'I'
    def apply()(using ByteBuf) = this

  case object ParseComplete extends BackendMessage, Decoder[ParseComplete.type]:
    inline final val Tag = '1'
    def apply()(using ByteBuf) = this

  case class ParameterDescription(parameters: Short) extends BackendMessage
  object ParameterDescription extends Decoder[ParameterDescription]:
    inline final val Tag = 't'
    def apply()(using buf: ByteBuf) =
      val parameters = buf.readShort
      (1 to parameters).foreach(_ => buf.readInt)
      ParameterDescription(parameters)

  case class RowDescription(fields: Seq[RowDescription.Field]) extends BackendMessage:
    override def toString = s"RowDescription(${fields.size} fields:${fields.mkString("\n", "\n", "")})"
    val length = fields.foldLeft(0)((s, a) => s + a.datasize)
  object RowDescription extends Decoder[RowDescription]:
    case class Field(name: String, tableoid: Int, column: Short, dataoid: Int, datasize: Short, modifier: Int, format: Short):
      override def toString =
        s"Field(name = $name, column = $column, typeoid = $dataoid, codec = ${Codec.nameForOid(dataoid)}, length = $datasize)"
    inline final val Tag = 'T'
    def apply()(using buf: ByteBuf) =
      val fields =
        Range(0, buf.readShort).map(_ =>
          Field(buf.readUtf8z, buf.readInt, buf.readShort, buf.readInt, buf.readShort, buf.readInt, buf.readShort)
        )
      val res = RowDescription(fields)
      println(s"$res")
      res

  case object BindComplete extends BackendMessage:
    inline final val Tag = '2'
    def apply()(using ByteBuf) = this

  case object CloseComplete extends BackendMessage:
    inline final val Tag = '3'
    def apply()(using ByteBuf) = this

  case object NoData extends BackendMessage:
    inline final val Tag = 'n'
    def apply()(using ByteBuf) = this

  case class ErrorResponse(errors: Seq[(Char, String)]) extends BackendMessage
  object ErrorResponse extends Decoder[ErrorResponse]:
    inline final val Tag = 'E'
    def apply()(using buf: ByteBuf) =
      var errors = Seq.empty[(Char, String)]
      var more = true
      while more do
        buf.readByte match
          case 0    => more = false
          case code => errors = errors :+ (code.toChar, buf.readUtf8z)
      ErrorResponse(errors)

  case class SslResponse(indicator: Char) extends BackendMessage

end BackendMessage

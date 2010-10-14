package org.broadinstitute.sting.queue.util

import org.apache.commons.mail.{MultiPartEmail, EmailAttachment}
import java.io.{FileReader, File}
import javax.mail.internet.InternetAddress
import scala.collection.JavaConversions

/**
 * Encapsulates a message to be sent over email.
 */
class EmailMessage extends Logging {
  var from: String = _
  var to: List[String] = Nil
  var cc: List[String] = Nil
  var bcc: List[String] = Nil
  var subject: String = _
  var body: String = _
  var attachments: List[File] = Nil

  /**
   * Sends the email and throws an exception if the email can't be sent.
   * @param settings The server settings for the email.
   */
  def send(settings: EmailSettings) = {
    val email = new MultiPartEmail

    email.setHostName(settings.host)
    email.setSmtpPort(settings.port)
    email.setTLS(settings.tls)
    if (settings.ssl) {
      email.setSSL(true)
      email.setSslSmtpPort(settings.port.toString)
    }

    if (settings.username != null && settings.password != null && settings.passwordFile != null) {
      val password = {
        if (settings.passwordFile != null) {
          val reader = new FileReader(settings.passwordFile)
          try {
            org.apache.commons.io.IOUtils.toString(reader)
          } finally {
            org.apache.commons.io.IOUtils.closeQuietly(reader)
          }
        } else {
          settings.password
        }
      }
      email.setAuthentication(settings.username, password)
    }

    email.setFrom(this.from)
    if (this.subject != null)
      email.setSubject(this.subject)
    if (this.body != null)
      email.setMsg(this.body)
    if (this.to.size > 0)
      email.setTo(convert(this.to))
    if (this.cc.size > 0)
      email.setCc(convert(this.cc))
    if (this.bcc.size > 0)
      email.setBcc(convert(this.bcc))

    for (file <- this.attachments) {
      val attachment = new EmailAttachment
      attachment.setDisposition(EmailAttachment.ATTACHMENT)
      attachment.setPath(file.getAbsolutePath)
      attachment.setDescription(file.getAbsolutePath)
      attachment.setName(file.getName)
      email.attach(attachment)
    }

    email.send
  }

  /**
   * Tries twice 30 seconds apart to send the email.  Then logs the message if it can't be sent.
   * @param settings The server settings for the email.
   */
  def trySend(settings: EmailSettings) = {
    try {
      Retry.attempt(() => send(settings), .5)
    } catch {
      case e => logger.error("Error sending message: %n%s".format(this.toString), e)
    }
  }

  /**
   * Converts the email addresses to a collection of InternetAddress which can bypass client side validation,
   * specifically that the domain is specified.
   * @param addresses List of email addresses.
   * @return java.util.List of InternetAddress'es
   */
  private def convert(addresses: List[String]) = {
    JavaConversions.asList(addresses.map(address => new InternetAddress(address, false)))
  }

  override def toString = {
    """|
    |From: %s
    |To: %s
    |Cc: %s
    |Bcc: %s
    |Subject: %s
    |
    |%s
    |
    |Attachments:
    |%s
    |""".stripMargin.trim.format(
      this.from, this.to.mkString(", "),
      this.cc.mkString(", "), this.bcc.mkString(", "),
      this.subject, this.body,
      this.attachments.map(_.getAbsolutePath).mkString("%n".format()))
  }
}

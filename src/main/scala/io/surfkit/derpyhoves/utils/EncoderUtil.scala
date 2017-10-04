package io.surfkit.derpyhoves.utils

import java.security.MessageDigest

/**
  * Created by suroot on 07/09/17.
  */
object EncoderUtil {

  def encodeSHA512(data: String, salt: String): String = {
    val md = MessageDigest.getInstance("SHA-512")
    md.update(salt.getBytes("UTF-8"))
    val bytes = md.digest(data.getBytes("UTF-8"))
    val sb = new StringBuilder()
    bytes.foreach(b => sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1)) )
    sb.toString()
  }

}

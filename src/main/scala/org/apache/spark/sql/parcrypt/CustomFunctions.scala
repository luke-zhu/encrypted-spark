package org.apache.spark.sql.parcrypt

import java.util.Base64

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec


// TODO: these should be Spark native functions
object CustomFunctions {

  lazy val kms: KMS = new KMS

  // TODO: IV
  // TODO: homomorphic encryption
  def decrypt(value: String, table: String, column: String): String = {
    val cipher = kms.cipher(table, column, Cipher.DECRYPT_MODE)
    new String(cipher.doFinal(Base64.getDecoder.decode(value)), "UTF-8")
  }

  def encrypt(value: String, table: String, column: String): String = {
    val cipher = kms.cipher(table, column, Cipher.ENCRYPT_MODE)
    Base64.getEncoder.encodeToString(cipher.doFinal(value.getBytes("UTF-8")))
  }
}

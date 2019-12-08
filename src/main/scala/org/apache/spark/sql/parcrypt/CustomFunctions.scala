package org.apache.spark.sql.parcrypt

import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.daead.DeterministicAeadKeyTemplates
import com.google.crypto.tink.{Aead, CleartextKeysetHandle, DeterministicAead, JsonKeysetReader}

// TODO: these should be Spark native functions
object CustomFunctions {
  // TODO: IV fns?
  // TODO: homomorphic encryption
  def decryptRnd(value: Array[Byte], table: String, column: String, kms: KMS): Array[Byte] = {
    val key = kms.dataKey(table, column, AeadKeyTemplates.AES128_GCM)
    val keysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(key))
    val aead = keysetHandle.getPrimitive(classOf[Aead])

    aead.decrypt(value, null) // TODO: use associated data?
  }

  def encryptRnd(value: Array[Byte], table: String, column: String, kms: KMS): Array[Byte] = {
    val key = kms.dataKey(table, column, AeadKeyTemplates.AES128_GCM)
    val keysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(key))
    val aead = keysetHandle.getPrimitive(classOf[Aead])

    aead.encrypt(value, null)
  }

  def decryptDet(value: Array[Byte], table: String, column: String, kms: KMS): Array[Byte] = {
    val key = kms.dataKey(table, column, DeterministicAeadKeyTemplates.AES256_SIV)
    val keysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(key))
    val daed = keysetHandle.getPrimitive(classOf[DeterministicAead])

    // TODO: non-fixed associated data? associated data cannot be null here otherwise
    //  an error will be thrown
    daed.decryptDeterministically(value, "abc".getBytes())
  }

  def encryptDet(value: Array[Byte], table: String, column: String, kms: KMS): Array[Byte] = {
    val key = kms.dataKey(table, column, DeterministicAeadKeyTemplates.AES256_SIV)
    val keysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(key))
    val daed = keysetHandle.getPrimitive(classOf[DeterministicAead])

    daed.encryptDeterministically(value, "abc".getBytes())
  }
}

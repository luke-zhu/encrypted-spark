package org.apache.spark.sql.parcrypt

import com.google.crypto.tink.{Aead, CleartextKeysetHandle, DeterministicAead, JsonKeysetReader}

// TODO: these should be Spark native functions
object functions {
  // TODO: IV fns?
  // TODO: homomorphic encryption
  def decryptRnd(value: Array[Byte], key: Array[Byte]): Array[Byte] = {
    val keysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(key))
    val aead = keysetHandle.getPrimitive(classOf[Aead])

    aead.decrypt(value, null) // TODO: use associated data?
  }

  def encryptRnd(value: Array[Byte], key: Array[Byte]): Array[Byte] = {
    val keysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(key))
    val aead = keysetHandle.getPrimitive(classOf[Aead])

    aead.encrypt(value, null)
  }

  def decryptDet(value: Array[Byte], key: Array[Byte]): Array[Byte] = {
    val keysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(key))
    val daed = keysetHandle.getPrimitive(classOf[DeterministicAead])

    // TODO: non-fixed associated data? associated data cannot be null here otherwise
    //  an error will be thrown
    daed.decryptDeterministically(value, "abc".getBytes())
  }

  def encryptDet(value: Array[Byte], key: Array[Byte]): Array[Byte] = {
    val keysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(key))
    val daed = keysetHandle.getPrimitive(classOf[DeterministicAead])

    daed.encryptDeterministically(value, "abc".getBytes())
  }
}

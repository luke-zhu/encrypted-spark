package org.apache.spark.sql.parcrypt

import java.io.ByteArrayOutputStream

import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.daead.DeterministicAeadKeyTemplates
import com.google.crypto.tink.{CleartextKeysetHandle, JsonKeysetWriter, KeysetHandle}
import org.apache.spark.sql.QueryTest
import org.apache.spark.sql.functions.{col, udf}
import org.apache.spark.sql.parcrypt.Cryptdb._
import org.apache.spark.sql.test.SharedSparkSession

class CoreSuite extends QueryTest with SharedSparkSession {

  import testImplicits._

  test("rnd") {
    // TODO: reduce boilerplate at top of every test
    TinkConfig.register()
    val keysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES128_GCM)
    val keysetStream = new ByteArrayOutputStream()

    CleartextKeysetHandle.write(keysetHandle, JsonKeysetWriter.withOutputStream(keysetStream))
    val masterKey = keysetStream.toByteArray

    val plaintext = "rnd-plaintext"
    val ciphertext1 = encryptRnd(plaintext.getBytes(), masterKey)
    val ciphertext2 = encryptRnd(plaintext.getBytes(), masterKey)

    assert(!(ciphertext1 sameElements ciphertext2))

    val output = decryptRnd(ciphertext1, masterKey)

    assert(output === plaintext.getBytes())
  }

  test("rnd-df") {
    TinkConfig.register()
    val keysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES128_GCM)
    val keysetStream = new ByteArrayOutputStream()

    CleartextKeysetHandle.write(keysetHandle, JsonKeysetWriter.withOutputStream(keysetStream))
    val masterKey = keysetStream.toByteArray

    val encryptUDF = udf((value: Array[Byte]) => encryptRnd(value, masterKey))
    val decryptUDF = udf((value: Array[Byte]) => decryptRnd(value, masterKey))

    val df = Seq(("abc", "def")).toDF("a", "b")
    val first = df
      .select(encryptUDF(col("a")))
      .select(decryptUDF(col("UDF(a)")))
      .first()
    assert(new String(first(0).asInstanceOf[Array[Byte]]) === "abc")
  }

  test("det") {
    TinkConfig.register()
    val keysetHandle = KeysetHandle.generateNew(DeterministicAeadKeyTemplates.AES256_SIV)
    val keysetStream = new ByteArrayOutputStream()
    CleartextKeysetHandle.write(keysetHandle, JsonKeysetWriter.withOutputStream(keysetStream))
    val masterKey = keysetStream.toByteArray

    val plaintext = "det-plaintext"
    val ciphertext1 = encryptDet(plaintext.getBytes(), masterKey)
    val ciphertext2 = encryptDet(plaintext.getBytes(), masterKey)

    assert(ciphertext1 sameElements ciphertext2)

    val output = decryptDet(ciphertext1, masterKey)

    assert(output === plaintext.getBytes())
  }

  test("det-df") {
    TinkConfig.register()
    val keysetHandle = KeysetHandle.generateNew(DeterministicAeadKeyTemplates.AES256_SIV)
    val keysetStream = new ByteArrayOutputStream()

    CleartextKeysetHandle.write(keysetHandle, JsonKeysetWriter.withOutputStream(keysetStream))
    val masterKey = keysetStream.toByteArray

    val encryptUDF = udf((value: Array[Byte]) => encryptDet(value, masterKey))
    val decryptUDF = udf((value: Array[Byte]) => decryptDet(value, masterKey))

    val df = Seq(("abc", "def"), ("abc", "def")).toDF("a", "b")
    val encryptedDF = df
      .select(encryptUDF(col("a")))

    assert(encryptedDF.distinct().count() == 1)

    val decryptedDF = encryptedDF
      .select(decryptUDF(col("UDF(a)")))

    assert(encryptedDF.distinct().count() == 1)
    assert(new String(decryptedDF.first.get(0).asInstanceOf[Array[Byte]]) == "abc")
  }
}

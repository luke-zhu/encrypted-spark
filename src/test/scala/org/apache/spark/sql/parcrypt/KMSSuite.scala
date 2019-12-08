package org.apache.spark.sql.parcrypt

import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.config.TinkConfig
import org.scalatest.FunSuite

class KMSSuite extends FunSuite {
  test("fetchKey") {
    TinkConfig.register()
    val kms = new KMS()
    val key1 = kms.dataKey("doggo", "dorgo", AeadKeyTemplates.AES128_GCM)
    val key2 = kms.dataKey("doggo", "dorgo", AeadKeyTemplates.AES128_GCM)
    assert(key1 sameElements key2)
  }
}

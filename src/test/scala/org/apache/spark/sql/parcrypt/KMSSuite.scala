package org.apache.spark.sql.parcrypt

import org.scalatest.FunSuite

class KMSSuite extends FunSuite {
  test("fetchKey") {
    val kms = new KMS()
    val key1 = kms.dataKey("doggo", "dorgo")
    val key2 = kms.dataKey("doggo", "dorgo")
    assert(key1 sameElements key2)
  }
}

package org.apache.spark.sql.parcrypt

import org.scalatest.FunSuite

class KMSSuite extends FunSuite {
  test("fetchKey") {
    val kms = new KMS()
    kms.dataKey("doggo", "horse")
    kms.dataKey("doggo", "horse")
  }
}

package org.apache.spark.sql.parcrypt

import org.apache.spark.sql.QueryTest
import org.apache.spark.sql.functions.{col, udf}
import org.apache.spark.sql.parcrypt.CustomFunctions._
import org.apache.spark.sql.test.SharedSparkSession

class CoreSuite extends QueryTest with SharedSparkSession {

  import testImplicits._

  test("det") {
    val kms = new KMS

    val plaintext = "det-plaintext"
    val ciphertext1 = encrypt(plaintext, "det", "a")
    val ciphertext2 = encrypt(plaintext, "det", "a")

    assert(ciphertext1 sameElements ciphertext2)

    val output = decrypt(ciphertext1, "det", "a")

    assert(output == plaintext)
  }

  test("det-df") {
    val kms = new KMS

    val encryptUDF = udf((value: String) => encrypt(value, "dets", "a"))
    val decryptUDF = udf((value: String) => decrypt(value, "dets", "a"))

    val df = Seq(("abc", "def"), ("abc", "def")).toDF("a", "b")
    val encryptedDF = df
      .select(encryptUDF(col("a")))

    assert(encryptedDF.distinct().count() == 1)

    val decryptedDF = encryptedDF
      .select(decryptUDF(col("UDF(a)")))

    assert(decryptedDF.distinct().count() == 1)
    assert(decryptedDF.first.get(0).asInstanceOf[String] == "abc")
  }
}

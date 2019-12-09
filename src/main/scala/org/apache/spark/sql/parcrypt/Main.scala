package org.apache.spark.sql.parcrypt

import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.sql.functions.{col, udf}

object Main {
  def main(args: Array[String]): Unit = {
    // This program evaluates how long it takes to load and save encrypted files
    val spark = SparkSession
      .builder()
      .appName("Test encrypted")
      .getOrCreate()

    val encryptUDF = udf((value: String) => CustomFunctions.encrypt(value, "rnd-bm", "col"))
    val decryptUDF = udf((value: String) => CustomFunctions.decrypt(value, "rnd-bm", "col"))

    //    val df = spark.range(0, 100000000).toDF("a")
    //    val dfStr = df.withColumn("a", df.col("a").cast("STRING"))
    //
    //    val startTime = System.currentTimeMillis()
    //    dfStr.write.mode("overwrite").parquet("bench/plaintext")
    //    val endTime = System.currentTimeMillis()
    //    println("Plaintext write:", (endTime - startTime).asInstanceOf[Float] / 1000)
    val dfStr = spark.read.parquet("bench/plaintext")

    import spark.implicits._
    val encryptedDFStr = dfStr.map(row => {
      CustomFunctions.encrypt(row.getString(0), "rnd-bm", "col")
    }).toDF("a")

    val startTimeEnc = System.currentTimeMillis()
    encryptedDFStr.write.mode("overwrite").parquet("bench/encrypted")
    val endTimeEnc = System.currentTimeMillis()
    println("Encrypted write:", (endTimeEnc - startTimeEnc).asInstanceOf[Float] / 1000)

    val startTime = System.currentTimeMillis()
    dfStr.write.mode("overwrite").parquet("bench/plaintext2")
    val endTime = System.currentTimeMillis()
    println("Plaintext write:", (endTime - startTime).asInstanceOf[Float] / 1000)
  }
}

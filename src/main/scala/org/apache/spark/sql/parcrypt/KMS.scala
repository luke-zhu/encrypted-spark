package org.apache.spark.sql.parcrypt

import java.nio.ByteBuffer

import com.amazonaws.services.kms.AWSKMSClientBuilder
import com.amazonaws.services.kms.model.{DecryptRequest, GenerateDataKeyRequest}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import scalaj.http._

import scala.collection.mutable

class KMS {

  // cache of keys that are already loaded
  private val cache: mutable.Map[(String, String), Array[Byte]] = mutable.HashMap()

  // TODO: handling revision keys
  def dataKey(table: String, column: String): Array[Byte] = {
    val dataKeyMaybe = cache.get((table, column))
    dataKeyMaybe match {
      case Some(dataKey) =>
        println("Found data key in cache")
        dataKey
      case None =>
        // TODO: do we need to specify keys here? We don't necessarily want to use the
        //  default AWSCredentialProvider keys.
        // TODO: use the values here for creds if set:
        //        val properties: util.Map[String, String] = new util.HashMap[String, String]()
        //        properties.put("aws_access_key_id", spark.conf.get("org.apache.spark."))
        //        properties.put("aws_secret_access_key", spark.conf.get("org.apache.spark."))
        //        val profile = new BasicProfile("parcrypt", properties)
        //        val creds: ProfileStaticCredentialsProvider = new ProfileStaticCredentialsProvider(profile)
        println("Data key is not in cache")
        val kmsClient = AWSKMSClientBuilder.standard.build

        // TODO: again the table might be a path = problems
        // TODO: don't just make it localhost
        val url = "http://localhost:8080/keys/" + table + "/" + column
        println("Sending getDataKey request to data key server:", url)
        val response: HttpResponse[Array[Byte]] = Http(url).asBytes
        var dataKey: Array[Byte] = null
        if (response.code == 200) {
          println("Found data key in data key server, decrypting")
          val mapper = new ObjectMapper() with ScalaObjectMapper
          mapper.registerModule(DefaultScalaModule)
          val parsedJson = mapper.readValue[Map[String, Object]](response.body)

          // TODO: do something with the parsed value
          val encryptedDataKeyBuffer = parsedJson("data_key")
          println(encryptedDataKeyBuffer)
          throw new RuntimeException()
          //          val encryptedDataKey: Array[Byte] = encryptedDataKeyBuffer.array()
          //          println("Encrypted data key:", new String(encryptedDataKeyBuffer.array()))array
          //
          //          val encryptedBuffer: ByteBuffer = ByteBuffer.wrap(encryptedDataKey)
          //          val request = new DecryptRequest().withCiphertextBlob(encryptedBuffer)
          //          val result = kmsClient.decrypt(request) // TODO: handle failures
          //          val decryptedBuffer = result.getPlaintext
          //
          //          dataKey = if (decryptedBuffer.hasArray) {
          //            decryptedBuffer.array()
          //          } else {
          //            new Array[Byte](decryptedBuffer.remaining)
          //          }
        } else if (response.code == 404) {
          
          println("Did not find data key in data key server, requesting new key from AWS")
          // TODO: not hardcode keyId
          val request = new GenerateDataKeyRequest().withKeyId("alias/test").withNumberOfBytes(32)
          val result = kmsClient.generateDataKey(request)
          val keyBuffer = result.getPlaintext

          dataKey = keyBuffer.array()

          val encryptedDataKey = new String(result.getCiphertextBlob.array())
          println("Putting encrypted key in data key server")
          // TODO: audit putting the keys
          // TODO: actual string formatting
          Http(url)
            .put("{\"data_key\":\"" + encryptedDataKey + "\"}")
            .asBytes
        } else {
          throw new RuntimeException("Failed to fetch key")
        }

        cache.put((table, column), dataKey)
        println("Returning decrypted data key")
        dataKey
    }
  }
}

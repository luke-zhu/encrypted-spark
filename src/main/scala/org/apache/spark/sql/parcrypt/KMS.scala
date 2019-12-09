package org.apache.spark.sql.parcrypt

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64

import com.amazonaws.services.kms.AWSKMSClientBuilder
import com.amazonaws.services.kms.model.{DecryptRequest, EncryptRequest, GenerateDataKeyRequest}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.apache.spark.sql.parcrypt.CustomFunctions.kms
import scalaj.http._

import scala.collection.mutable

class KMS extends Serializable {

  // cache of keys that are already loaded
  private val cache: mutable.Map[(String, String, Int), Cipher] = mutable.HashMap()

  def cipher(table: String, column: String, opmode: Int): Cipher = {
    val cipherOption = cache.get((table, column, opmode))
    cipherOption match {
      case None =>
        val key = dataKey(table, column)
        val cipher = Cipher.getInstance("AES")
        cipher.init(opmode, new SecretKeySpec(key, "AES"))
        cache.put((table, column, opmode), cipher)
        cipher
      case Some(cipher) => cipher
    }
  }

  // TODO: handling revision keys
  def dataKey(table: String, column: String): Array[Byte] = {

    // TODO: do we need to specify keys here? We don't necessarily want to use the
    //  default AWSCredentialProvider keys.
    // TODO: use the values here for creds if set:
    //        val properties: util.Map[String, String] = new util.HashMap[String, String]()
    //        properties.put("aws_access_key_id", spark.conf.get("org.apache.spark."))
    //        properties.put("aws_secret_access_key", spark.conf.get("org.apache.spark."))
    //        val profile = new BasicProfile("parcrypt", properties)
    //        val creds: ProfileStaticCredentialsProvider = new ProfileStaticCredentialsProvider(profile)
    // println("Data key is not in cache")
    val kmsClient = AWSKMSClientBuilder.standard.build

    // TODO: again the table might be a path = problems
    // TODO: don't just make it localhost
    val url = "http://localhost:8080/keys/" + table + "/" + column
    // println("Sending getDataKey request to data key server:", url)

    // We set dataKey in this match block
    var dataKey: Array[Byte] = null
    val response: HttpResponse[Array[Byte]] = Http(url).asBytes
    response.code match {
      case 200 =>
        val mapper = new ObjectMapper() with ScalaObjectMapper
        mapper.registerModule(DefaultScalaModule)
        val parsedJson = mapper.readValue[Map[String, Object]](response.body)

        val encryptedDataKeyString: String = parsedJson("data_key").asInstanceOf[String]
        val encryptedDataKeyArray: Array[Byte] = Base64.getDecoder.decode(encryptedDataKeyString)

        val encryptedBuffer: ByteBuffer = ByteBuffer.wrap(encryptedDataKeyArray)
        val request = new DecryptRequest().withCiphertextBlob(encryptedBuffer)
        val result = kmsClient.decrypt(request) // TODO: handle failures
        val decryptedBuffer = result.getPlaintext
        dataKey = decryptedBuffer.array()
      case 404 =>
        // println("Did not find data key in data key server, generating new key")
        val request = new GenerateDataKeyRequest().withKeyId("alias/test").withKeySpec("AES_128")
        val result = kmsClient.generateDataKey(request)

        dataKey = result.getPlaintext.array()
        // encode in base64 to avoid JSON parse failure
        val encryptedDataKey = Base64.getEncoder.encodeToString(result.getCiphertextBlob.array())

        // println("Putting encrypted key in data key server")
        // TODO: audit putting the keys
        // TODO: actual string formatting
        val mapper = new ObjectMapper() with ScalaObjectMapper
        mapper.registerModule(DefaultScalaModule)
        val payload = "{ \"data_key\":\"" + encryptedDataKey + "\"}"
        Http(url)
          .put(payload)
          .asBytes
      case _ => throw new RuntimeException("Failed to fetch key")
    }

    // println("Returning decrypted data key")
    // println(dataKey.length)
    return dataKey
  }
}

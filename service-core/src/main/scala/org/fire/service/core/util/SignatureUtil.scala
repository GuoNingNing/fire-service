package org.fire.service.core.util

import java.net.URLEncoder
import java.util.Base64
import javax.crypto.{Cipher, Mac}
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}

/**
  * Created by cloud on 18/8/31.
  *
  * 根据key生成对应的签名,可以有效防止签名数据被串改
  */
case class SignatureParams(id: String,
                           timestamp: Long,
                           version: Float,
                           params: Map[String, String]) {
  override def toString: String = {
    val str = s"id:$id&timestamp:$timestamp&version:$version"
    val ps = params.map(kv => s"${kv._1}:${URLEncoder.encode(kv._2,"UTF-8")}").toList.sortBy(s => s.hashCode).mkString("&")
    s"$str&$ps"
  }
}

object SignatureUtil {
  def createSignature(jwtParams: SignatureParams, secretKey: String): String = {
    val signature = hmacSHA1Encrypt(jwtParams.toString, secretKey)
    Base64.getEncoder.encodeToString(signature)
  }

  def validateSignature(jwtParams: SignatureParams, secretKey: String, signature: String): Boolean = {
    val s = createSignature(jwtParams, secretKey)
    if (s == signature && System.currentTimeMillis() - jwtParams.timestamp <= 10000L) {
      true
    } else {
      false
    }
  }

  def hmacSHA1Encrypt(encryptText: String, secret: String): Array[Byte] = {
    val macName = "HmacSHA1"
    val encoding = "UTF-8"

    val secretKey = new SecretKeySpec(secret.getBytes(encoding), macName)
    val mac = Mac.getInstance(macName)
    mac.init(secretKey)
    mac.doFinal(encryptText.getBytes(encoding))
  }

  def encrypt(secret: String)
             (f: (Cipher, SecretKeySpec, IvParameterSpec) => String): String = {
    val secretKey = s"$secret!@#qweasdzxc0987".substring(0,16)
    val coding = "UTF-8"
    val algorithm = "AES"
    val ivString = "z.cloud.test.001"
    val workMode = "CBC"
    val fillMode = "PKCS5Padding"
    val secretKeySpec = new SecretKeySpec(secretKey.getBytes(coding), algorithm)
    val iv = new IvParameterSpec(ivString.getBytes(coding))
    val cipher = Cipher.getInstance(s"$algorithm/$workMode/$fillMode")
    f(cipher,secretKeySpec,iv)
  }

  def aesEncrypt(data: String, secret: String): String  = encrypt(secret){
    (c,s,iv) =>
      c.init(Cipher.ENCRYPT_MODE,s,iv)
      Base64.getEncoder.encodeToString(c.doFinal(data.getBytes("UTF-8")))
  }

  def aesDecrypt(data: String, secret: String): String = encrypt(secret){
    (c,s,iv) =>
      c.init(Cipher.DECRYPT_MODE,s,iv)
      new String(c.doFinal(Base64.getDecoder.decode(data)), "UTF-8")
  }
}

package oldigital.com.plugins

import com.google.gson.Gson
import com.zkteco.biometric.FingerprintSensorEx
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.*
import javax.imageio.ImageIO
import javax.imageio.stream.ImageOutputStream

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        get("/getfingerprint") {
            getFingerprintHandler(call)
        }
    }
}

suspend fun getFingerprintHandler(call: ApplicationCall) {
    val imgbuf: ByteArray
    val template = ByteArray(2048)
    val templateLen = IntArray(1)

    val paramValue = ByteArray(4)
    val size = IntArray(1)

    println("Init: " + FingerprintSensorEx.Init())

    var device = FingerprintSensorEx.OpenDevice(0)
    println("device: $device")

    //get fingerprint dimensions
    size[0] = 4
    println("Parameter: " + FingerprintSensorEx.GetParameters(device, 1, paramValue, size))
    var fpWidth = byteArrayToInt(paramValue)
    size[0] = 4
    FingerprintSensorEx.GetParameters(device, 2, paramValue, size)
    var fpHeight = byteArrayToInt(paramValue)

    //set image byuffer
    imgbuf = ByteArray(fpWidth * fpHeight)

    templateLen[0] = 2048

    var elapsedTime = 0L
    var value = -8

    while (value != 0 && elapsedTime < 10000) {
        value = FingerprintSensorEx.AcquireFingerprint(device, imgbuf, template, templateLen)
        if (value != 0) {
            delay(500)
            elapsedTime += 500
        }
    }

    val imageBase64 = if (value == 0) {
        val tiff = writeTiff(imgbuf, fpWidth, fpHeight)
        if (tiff!=null)
            Base64.getEncoder().encodeToString(tiff)
        else
            ""
    } else {
        ""
    }

    val response = FingerprintResponse(value, imageBase64)

    var gson = Gson()
    call.respondText( gson.toJson(response), contentType = ContentType.Application.Json)
}

data class FingerprintResponse(val responseCode: Int, val image: String)
fun byteArrayToInt(bytes: ByteArray): Int {
    var number = bytes[0].toInt() and 0xFF
    number = number or (bytes[1].toInt() shl 8 and 0xFF00)
    number = number or (bytes[2].toInt() shl 16 and 0xFF0000)
    number = number or (bytes[3].toInt() shl 24 and -0x1000000)
    return number
}

fun writeTiff(imageBuf: ByteArray, nWidth: Int, nHeight: Int): ByteArray? {
    return try {
        val image = BufferedImage(nWidth, nHeight, BufferedImage.TYPE_BYTE_GRAY)
        image.raster.setDataElements(0, 0, nWidth, nHeight, imageBuf)

        val writer = ImageIO.getImageWritersByFormatName("TIFF").next()
        val output = ByteArrayOutputStream()
        val imageOutputStream: ImageOutputStream = ImageIO.createImageOutputStream(output)
        writer.output = imageOutputStream
        writer.write(image)
        writer.dispose()
        imageOutputStream.close()

        output.toByteArray()
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}
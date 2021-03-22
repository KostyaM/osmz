package com.vsb.kru13.osmzhttpserver

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.renderscript.*


object ProcessImageUtils {
    var instance: ProcessImageUtilsInstance? = null


    fun getInstance(context: Context, image: Image): ProcessImageUtilsInstance {
        if (instance == null)
            instance = ProcessImageUtilsInstance(
                    context = context,
                    imageHeight = image.height,
                    imageWidth = image.width
            )
        return instance!!
    }

    class ProcessImageUtilsInstance(
            context: Context,
            imageWidth: Int,
            imageHeight: Int
    ) {

        val rs: RenderScript = RenderScript.create(context)
        val rgbaType: Type.Builder = Type.Builder(rs, Element.RGBA_8888(rs))
                .setX(imageWidth)
                .setY(imageHeight)
        val out: Allocation = Allocation.createTyped(
                rs, rgbaType.create(), Allocation.USAGE_SCRIPT)

        val bitmap: Bitmap = Bitmap.createBitmap(
                imageWidth, imageHeight, Bitmap.Config.ARGB_8888)

        fun yuv420ToBitmap(image: Image): Bitmap? {
            val script: ScriptIntrinsicYuvToRGB = ScriptIntrinsicYuvToRGB.create(
                    rs, Element.U8_4(rs))

            val yuvByteArray: ByteArray = yuv420ToByteArray(image)
            val yuvType: Type.Builder = Type.Builder(rs, Element.U8(rs))
                    .setX(yuvByteArray.size)
            val input: Allocation = Allocation.createTyped(
                    rs, yuvType.create(), Allocation.USAGE_SCRIPT)



            input.copyFrom(yuvByteArray)
            script.setInput(input)
            script.forEach(out)

            out.copyTo(bitmap)
            return bitmap
        }

        private fun yuv420ToByteArray(image: Image): ByteArray {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize: Int = yBuffer.remaining()
            val uSize: Int = uBuffer.remaining()
            val vSize: Int = vBuffer.remaining()

            val byteArray = ByteArray(ySize + uSize + vSize)
            yBuffer.get(byteArray, 0, ySize);
            vBuffer.get(byteArray, ySize, vSize);
            uBuffer.get(byteArray, ySize + vSize, uSize);
            return byteArray
        }
    }
}
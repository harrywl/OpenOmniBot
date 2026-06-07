package cn.com.omnimind.assists.detection.detectors.icon

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.IOException

/**
 * Tool for exporting icon template signatures to JSON file.
 * 
 * The exported signatures can be used for:
 * 1. Pre-filtering templates before expensive multiScaleMatch
 * 2. Quick template similarity search
 * 3. Template deduplication
 */
object IconTemplateSignatureExporter {

    private const val TEMPLATE_SIZE = 64

    /**
     * Signature entry for a single template.
     */
    data class SignatureEntry(
        val name: String,
        val variant: String,
        val phash64: String,    // "0x..." format
        val edgeDensity: Float,
        val centerEnergy: Float,
        val hu: DoubleArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SignatureEntry
            return name == other.name && variant == other.variant
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + variant.hashCode()
            return result
        }
    }

    /**
     * Export signatures for all icon templates in assets/icons directory.
     * 
     * @param context Android context for accessing assets
     * @return JSON string containing all template signatures
     */
    fun exportSignatureJson(context: Context): String {
        val entries = ArrayList<SignatureEntry>()
        val mask = createInnerMask(TEMPLATE_SIZE)

        try {
            val am = context.assets
            val iconDirs = am.list("icons") ?: return buildEmptyJson()

            for (iconId in iconDirs) {
                val files = am.list("icons/$iconId") ?: continue
                for (file in files) {
                    if (!file.endsWith(".png")) continue

                    val variant = file.substringBeforeLast(".")
                    val path = "icons/$iconId/$file"

                    try {
                        val stream = am.open(path)
                        val bmp = BitmapFactory.decodeStream(stream)
                        stream.close()

                        if (bmp != null) {
                            val entry = computeSignature(bmp, iconId, variant, mask)
                            entries.add(entry)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            mask.release()
        }

        // Sort by name then variant for stable output
        entries.sortWith(compareBy({ it.name }, { it.variant }))

        return buildJson(entries)
    }

    /**
     * Export signature for a single template image from file path.
     */
    fun exportSingleSignature(
        bmp: android.graphics.Bitmap,
        name: String,
        variant: String
    ): SignatureEntry {
        val mask = createInnerMask(TEMPLATE_SIZE)
        val entry = computeSignature(bmp, name, variant, mask)
        mask.release()
        return entry
    }

    private fun computeSignature(
        bmp: android.graphics.Bitmap,
        name: String,
        variant: String,
        mask: Mat
    ): SignatureEntry {
        val mat = Mat()
        Utils.bitmapToMat(bmp, mat)

        // BGR conv
        if (mat.channels() == 4) {
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)
        }

        // Normalize
        val normGray = IconTemplateMatcher.normalizeIconPatch(mat, TEMPLATE_SIZE)
        val normGrayMasked = Mat()
        Core.bitwise_and(normGray, normGray, normGrayMasked, mask)

        val normEdgeMasked = Mat()
        Imgproc.Canny(normGrayMasked, normEdgeMasked, 50.0, 150.0)

        // Compute signatures
        val phash = IconTemplateMatcher.computePhash64(normGrayMasked)
        val edgeDensity = IconTemplateMatcher.computeEdgeDensity(normEdgeMasked, mask)
        val centerEnergy = IconTemplateMatcher.computeCenterEnergy(normEdgeMasked, mask)
        val huMoments = IconTemplateMatcher.computeHuMoments(normEdgeMasked)

        // Cleanup
        mat.release()
        normGray.release()
        normGrayMasked.release()
        normEdgeMasked.release()

        return SignatureEntry(
            name = name,
            variant = variant,
            phash64 = IconTemplateMatcher.phashToHex(phash),
            edgeDensity = edgeDensity,
            centerEnergy = centerEnergy,
            hu = huMoments
        )
    }

    private fun createInnerMask(size: Int): Mat {
        val mask = Mat.zeros(size, size, CvType.CV_8UC1)
        val center = Point(size / 2.0, size / 2.0)
        val radius = (size * 0.35).toInt()
        Imgproc.circle(mask, center, radius, Scalar(255.0), -1)
        return mask
    }

    private fun buildEmptyJson(): String {
        return JSONObject().apply {
            put("file", JSONArray())
        }.toString(2)
    }

    private fun buildJson(entries: List<SignatureEntry>): String {
        val root = JSONObject()
        val fileArray = JSONArray()

        for (entry in entries) {
            val obj = JSONObject().apply {
                put("name", entry.name)
                put("variant", entry.variant)
                put("phash64", entry.phash64)
                put("edgeDensity", entry.edgeDensity.toDouble())
                put("centerEnergy", entry.centerEnergy.toDouble())

                val huArray = JSONArray()
                for (h in entry.hu) {
                    huArray.put(h)
                }
                put("hu", huArray)
            }
            fileArray.put(obj)
        }

        root.put("file", fileArray)
        return root.toString(2)
    }
}

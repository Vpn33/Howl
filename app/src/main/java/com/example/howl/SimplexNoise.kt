package com.example.howl

/**
 * A 3D Simplex Noise implementation.
 * Based on OpenSimplex noise by Kurt Spencer.
 */
class SimplexNoise(seed: Long = 0L) {
    private val perm = IntArray(256)
    private val permGradIndex3D = IntArray(256)

    companion object {
        private val gradients3D = byteArrayOf(
            -11,  4,  4,     -4,  11,  4,    -4,  4,  11,
            11,  4,  4,      4,  11,  4,     4,  4,  11,
            -11, -4,  4,     -4, -11,  4,    -4, -4,  11,
            11, -4,  4,      4, -11,  4,     4, -4,  11,
            -11,  4, -4,     -4,  11, -4,    -4,  4, -11,
            11,  4, -4,      4,  11, -4,     4,  4, -11,
            -11, -4, -4,     -4, -11, -4,    -4, -4, -11,
            11, -4, -4,      4, -11, -4,     4, -4, -11
        )
        private const val STRETCH_CONSTANT_3D = -1.0 / 6
        private const val SQUISH_CONSTANT_3D = 1.0 / 3
        private const val NORM_CONSTANT_3D = 103.0
    }

    init {
        var s = seed
        val source = IntArray(256)
        for (i in 0..255) source[i] = i

        s = s * 6364136223846793005L + 1442695040888963407L
        s = s * 6364136223846793005L + 1442695040888963407L
        s = s * 6364136223846793005L + 1442695040888963407L
        for (i in 255 downTo 0) {
            s = s * 6364136223846793005L + 1442695040888963407L
            var r = ((s + 31) % (i + 1)).toInt()
            if (r < 0) r += i + 1
            perm[i] = source[r]
            permGradIndex3D[i] = (perm[i] % (gradients3D.size / 3) * 3)
            source[r] = source[i]
        }
    }

    fun random3D(x: Double, y: Double, z: Double): Double {
        val stretchOffset = (x + y + z) * STRETCH_CONSTANT_3D
        val xs = x + stretchOffset
        val ys = y + stretchOffset
        val zs = z + stretchOffset

        val xsb = fastFloor(xs)
        val ysb = fastFloor(ys)
        val zsb = fastFloor(zs)

        val squishOffset = (xsb + ysb + zsb) * SQUISH_CONSTANT_3D
        val xb = xsb + squishOffset
        val yb = ysb + squishOffset
        val zb = zsb + squishOffset

        val xIns = xs - xsb
        val yIns = ys - ysb
        val zIns = zs - zsb

        val inSum = xIns + yIns + zIns

        var dx0 = x - xb
        var dy0 = y - yb
        var dz0 = z - zb

        var dxExt0: Double; var dyExt0: Double; var dzExt0: Double
        var dxExt1: Double; var dyExt1: Double; var dzExt1: Double
        var xsvExt0: Int; var ysvExt0: Int; var zsvExt0: Int
        var xsvExt1: Int; var ysvExt1: Int; var zsvExt1: Int

        var value = 0.0
        if (inSum <= 1.0) {
            var aPoint = 0x01; var aScore = xIns
            var bPoint = 0x02; var bScore = yIns
            if (aScore >= bScore && zIns > bScore) { bScore = zIns; bPoint = 0x04 }
            else if (aScore < bScore && zIns > aScore) { aScore = zIns; aPoint = 0x04 }

            val wins = 1 - inSum
            if (wins > aScore || wins > bScore) {
                val c = if (bScore > aScore) bPoint else aPoint
                if ((c and 0x01) == 0) { xsvExt0 = xsb - 1; xsvExt1 = xsb; dxExt0 = dx0 + 1; dxExt1 = dx0 }
                else { xsvExt1 = xsb + 1; xsvExt0 = xsvExt1; dxExt1 = dx0 - 1; dxExt0 = dxExt1 }

                if ((c and 0x02) == 0) {
                    ysvExt1 = ysb; ysvExt0 = ysvExt1; dyExt1 = dy0; dyExt0 = dyExt1
                    if ((c and 0x01) == 0) { ysvExt1 -= 1; dyExt1 += 1.0 } else { ysvExt0 -= 1; dyExt0 += 1.0 }
                } else { ysvExt1 = ysb + 1; ysvExt0 = ysvExt1; dyExt1 = dy0 - 1; dyExt0 = dyExt1 }

                if ((c and 0x04) == 0) { zsvExt0 = zsb; zsvExt1 = zsb - 1; dzExt0 = dz0; dzExt1 = dz0 + 1 }
                else { zsvExt1 = zsb + 1; zsvExt0 = zsvExt1; dzExt1 = dz0 - 1; dzExt0 = dzExt1 }
            } else {
                val c = aPoint or bPoint
                if ((c and 0x01) == 0) { xsvExt0 = xsb; xsvExt1 = xsb - 1; dxExt0 = dx0 - 2 * SQUISH_CONSTANT_3D; dxExt1 = dx0 + 1 - SQUISH_CONSTANT_3D }
                else { xsvExt1 = xsb + 1; xsvExt0 = xsvExt1; dxExt0 = dx0 - 1 - 2 * SQUISH_CONSTANT_3D; dxExt1 = dx0 - 1 - SQUISH_CONSTANT_3D }

                if ((c and 0x02) == 0) { ysvExt0 = ysb; ysvExt1 = ysb - 1; dyExt0 = dy0 - 2 * SQUISH_CONSTANT_3D; dyExt1 = dy0 + 1 - SQUISH_CONSTANT_3D }
                else { ysvExt1 = ysb + 1; ysvExt0 = ysvExt1; dyExt0 = dy0 - 1 - 2 * SQUISH_CONSTANT_3D; dyExt1 = dy0 - 1 - SQUISH_CONSTANT_3D }

                if ((c and 0x04) == 0) { zsvExt0 = zsb; zsvExt1 = zsb - 1; dzExt0 = dz0 - 2 * SQUISH_CONSTANT_3D; dzExt1 = dz0 + 1 - SQUISH_CONSTANT_3D }
                else { zsvExt1 = zsb + 1; zsvExt0 = zsvExt1; dzExt0 = dz0 - 1 - 2 * SQUISH_CONSTANT_3D; dzExt1 = dz0 - 1 - SQUISH_CONSTANT_3D }
            }

            var attn0 = 2.0 - dx0 * dx0 - dy0 * dy0 - dz0 * dz0
            if (attn0 > 0) { attn0 *= attn0; value += attn0 * attn0 * extrapolate(xsb, ysb, zsb, dx0, dy0, dz0) }

            val dx1 = dx0 - 1 - SQUISH_CONSTANT_3D
            val dy1 = dy0 - 0 - SQUISH_CONSTANT_3D
            val dz1 = dz0 - 0 - SQUISH_CONSTANT_3D
            var attn1 = 2.0 - dx1 * dx1 - dy1 * dy1 - dz1 * dz1
            if (attn1 > 0) { attn1 *= attn1; value += attn1 * attn1 * extrapolate(xsb + 1, ysb, zsb, dx1, dy1, dz1) }

            val dx2 = dx0 - 0 - SQUISH_CONSTANT_3D
            val dy2 = dy0 - 1 - SQUISH_CONSTANT_3D
            var attn2 = 2.0 - dx2 * dx2 - dy2 * dy2 - dz1 * dz1
            if (attn2 > 0) { attn2 *= attn2; value += attn2 * attn2 * extrapolate(xsb, ysb + 1, zsb, dx2, dy2, dz1) }

            val dz3 = dz0 - 1 - SQUISH_CONSTANT_3D
            var attn3 = 2.0 - dx2 * dx2 - dy1 * dy1 - dz3 * dz3
            if (attn3 > 0) { attn3 *= attn3; value += attn3 * attn3 * extrapolate(xsb, ysb, zsb + 1, dx2, dy1, dz3) }

        } else if (inSum >= 2.0) {
            var aPoint = 0x06; var aScore = xIns
            var bPoint = 0x05; var bScore = yIns
            if (aScore <= bScore && zIns < bScore) { bScore = zIns; bPoint = 0x03 }
            else if (aScore > bScore && zIns < aScore) { aScore = zIns; aPoint = 0x03 }

            val wins = 3 - inSum
            if (wins < aScore || wins < bScore) {
                val c = if (bScore < aScore) bPoint else aPoint
                if ((c and 0x01) != 0) { xsvExt0 = xsb + 2; xsvExt1 = xsb + 1; dxExt0 = dx0 - 2 - 3 * SQUISH_CONSTANT_3D; dxExt1 = dx0 - 1 - 3 * SQUISH_CONSTANT_3D }
                else { xsvExt1 = xsb; xsvExt0 = xsvExt1; dxExt1 = dx0 - 3 * SQUISH_CONSTANT_3D; dxExt0 = dxExt1 }

                if ((c and 0x02) != 0) {
                    ysvExt1 = ysb + 1; ysvExt0 = ysvExt1; dyExt1 = dy0 - 1 - 3 * SQUISH_CONSTANT_3D; dyExt0 = dyExt1
                    if ((c and 0x01) != 0) { ysvExt1 += 1; dyExt1 -= 1.0 } else { ysvExt0 += 1; dyExt0 -= 1.0 }
                } else { ysvExt1 = ysb; ysvExt0 = ysvExt1; dyExt1 = dy0 - 3 * SQUISH_CONSTANT_3D; dyExt0 = dyExt1 }

                if ((c and 0x04) != 0) { zsvExt0 = zsb + 1; zsvExt1 = zsb + 2; dzExt0 = dz0 - 1 - 3 * SQUISH_CONSTANT_3D; dzExt1 = dz0 - 2 - 3 * SQUISH_CONSTANT_3D }
                else { zsvExt1 = zsb; zsvExt0 = zsvExt1; dzExt1 = dz0 - 3 * SQUISH_CONSTANT_3D; dzExt0 = dzExt1 }
            } else {
                val c = aPoint and bPoint
                if ((c and 0x01) != 0) { xsvExt0 = xsb + 1; xsvExt1 = xsb + 2; dxExt0 = dx0 - 1 - SQUISH_CONSTANT_3D; dxExt1 = dx0 - 2 - 2 * SQUISH_CONSTANT_3D }
                else { xsvExt1 = xsb; xsvExt0 = xsvExt1; dxExt0 = dx0 - SQUISH_CONSTANT_3D; dxExt1 = dx0 - 2 * SQUISH_CONSTANT_3D }

                if ((c and 0x02) != 0) { ysvExt0 = ysb + 1; ysvExt1 = ysb + 2; dyExt0 = dy0 - 1 - SQUISH_CONSTANT_3D; dyExt1 = dy0 - 2 - 2 * SQUISH_CONSTANT_3D }
                else { ysvExt1 = ysb; ysvExt0 = ysvExt1; dyExt0 = dy0 - SQUISH_CONSTANT_3D; dyExt1 = dy0 - 2 * SQUISH_CONSTANT_3D }

                if ((c and 0x04) != 0) { zsvExt0 = zsb + 1; zsvExt1 = zsb + 2; dzExt0 = dz0 - 1 - SQUISH_CONSTANT_3D; dzExt1 = dz0 - 2 - 2 * SQUISH_CONSTANT_3D }
                else { zsvExt1 = zsb; zsvExt0 = zsvExt1; dzExt0 = dz0 - SQUISH_CONSTANT_3D; dzExt1 = dz0 - 2 * SQUISH_CONSTANT_3D }
            }

            val dx3 = dx0 - 1 - 2 * SQUISH_CONSTANT_3D
            val dy3 = dy0 - 1 - 2 * SQUISH_CONSTANT_3D
            val dz3 = dz0 - 0 - 2 * SQUISH_CONSTANT_3D
            var attn3 = 2.0 - dx3 * dx3 - dy3 * dy3 - dz3 * dz3
            if (attn3 > 0) { attn3 *= attn3; value += attn3 * attn3 * extrapolate(xsb + 1, ysb + 1, zsb, dx3, dy3, dz3) }

            val dy2 = dy0 - 0 - 2 * SQUISH_CONSTANT_3D
            val dz2 = dz0 - 1 - 2 * SQUISH_CONSTANT_3D
            var attn2 = 2.0 - dx3 * dx3 - dy2 * dy2 - dz2 * dz2
            if (attn2 > 0) { attn2 *= attn2; value += attn2 * attn2 * extrapolate(xsb + 1, ysb, zsb + 1, dx3, dy2, dz2) }

            val dx1 = dx0 - 0 - 2 * SQUISH_CONSTANT_3D
            var attn1 = 2.0 - dx1 * dx1 - dy3 * dy3 - dz2 * dz2
            if (attn1 > 0) { attn1 *= attn1; value += attn1 * attn1 * extrapolate(xsb, ysb + 1, zsb + 1, dx1, dy3, dz2) }

            dx0 = dx0 - 1 - 3 * SQUISH_CONSTANT_3D
            dy0 = dy0 - 1 - 3 * SQUISH_CONSTANT_3D
            dz0 = dz0 - 1 - 3 * SQUISH_CONSTANT_3D
            var attn0 = 2.0 - dx0 * dx0 - dy0 * dy0 - dz0 * dz0
            if (attn0 > 0) { attn0 *= attn0; value += attn0 * attn0 * extrapolate(xsb + 1, ysb + 1, zsb + 1, dx0, dy0, dz0) }

        } else {
            var aScore: Double; var aPoint: Int; var aIsFurtherSide: Boolean
            var bScore: Double; var bPoint: Int; var bIsFurtherSide: Boolean

            val p1 = xIns + yIns
            if (p1 > 1) { aScore = p1 - 1; aPoint = 0x03; aIsFurtherSide = true }
            else { aScore = 1 - p1; aPoint = 0x04; aIsFurtherSide = false }

            val p2 = xIns + zIns
            if (p2 > 1) { bScore = p2 - 1; bPoint = 0x05; bIsFurtherSide = true }
            else { bScore = 1 - p2; bPoint = 0x02; bIsFurtherSide = false }

            val p3 = yIns + zIns
            if (p3 > 1) {
                val score = p3 - 1
                if (aScore <= bScore && aScore < score) {
                    aPoint = 0x06; aIsFurtherSide = true }
                else if (aScore > bScore && bScore < score) {
                    bPoint = 0x06; bIsFurtherSide = true }
            } else {
                val score = 1 - p3
                if (aScore <= bScore && aScore < score) {
                    aPoint = 0x01; aIsFurtherSide = false }
                else if (aScore > bScore && bScore < score) {
                    bPoint = 0x01; bIsFurtherSide = false }
            }

            if (aIsFurtherSide == bIsFurtherSide) {
                if (aIsFurtherSide) {
                    dxExt0 = dx0 - 1 - 3 * SQUISH_CONSTANT_3D; dyExt0 = dy0 - 1 - 3 * SQUISH_CONSTANT_3D; dzExt0 = dz0 - 1 - 3 * SQUISH_CONSTANT_3D
                    xsvExt0 = xsb + 1; ysvExt0 = ysb + 1; zsvExt0 = zsb + 1

                    val c = aPoint and bPoint
                    if ((c and 0x01) != 0) { dxExt1 = dx0 - 2 - 2 * SQUISH_CONSTANT_3D; dyExt1 = dy0 - 2 * SQUISH_CONSTANT_3D; dzExt1 = dz0 - 2 * SQUISH_CONSTANT_3D; xsvExt1 = xsb + 2; ysvExt1 = ysb; zsvExt1 = zsb }
                    else if ((c and 0x02) != 0) { dxExt1 = dx0 - 2 * SQUISH_CONSTANT_3D; dyExt1 = dy0 - 2 - 2 * SQUISH_CONSTANT_3D; dzExt1 = dz0 - 2 * SQUISH_CONSTANT_3D; xsvExt1 = xsb; ysvExt1 = ysb + 2; zsvExt1 = zsb }
                    else { dxExt1 = dx0 - 2 * SQUISH_CONSTANT_3D; dyExt1 = dy0 - 2 * SQUISH_CONSTANT_3D; dzExt1 = dz0 - 2 - 2 * SQUISH_CONSTANT_3D; xsvExt1 = xsb; ysvExt1 = ysb; zsvExt1 = zsb + 2 }
                } else {
                    dxExt0 = dx0; dyExt0 = dy0; dzExt0 = dz0
                    xsvExt0 = xsb; ysvExt0 = ysb; zsvExt0 = zsb

                    val c = aPoint or bPoint
                    if ((c and 0x01) == 0) { dxExt1 = dx0 + 1 - SQUISH_CONSTANT_3D; dyExt1 = dy0 - 1 - SQUISH_CONSTANT_3D; dzExt1 = dz0 - 1 - SQUISH_CONSTANT_3D; xsvExt1 = xsb - 1; ysvExt1 = ysb + 1; zsvExt1 = zsb + 1 }
                    else if ((c and 0x02) == 0) { dxExt1 = dx0 - 1 - SQUISH_CONSTANT_3D; dyExt1 = dy0 + 1 - SQUISH_CONSTANT_3D; dzExt1 = dz0 - 1 - SQUISH_CONSTANT_3D; xsvExt1 = xsb + 1; ysvExt1 = ysb - 1; zsvExt1 = zsb + 1 }
                    else { dxExt1 = dx0 - 1 - SQUISH_CONSTANT_3D; dyExt1 = dy0 - 1 - SQUISH_CONSTANT_3D; dzExt1 = dz0 + 1 - SQUISH_CONSTANT_3D; xsvExt1 = xsb + 1; ysvExt1 = ysb + 1; zsvExt1 = zsb - 1 }
                }
            } else {
                val c1 = if (aIsFurtherSide) aPoint else bPoint
                val c2 = if (aIsFurtherSide) bPoint else aPoint

                if ((c1 and 0x01) == 0) { dxExt0 = dx0 + 1 - SQUISH_CONSTANT_3D; dyExt0 = dy0 - 1 - SQUISH_CONSTANT_3D; dzExt0 = dz0 - 1 - SQUISH_CONSTANT_3D; xsvExt0 = xsb - 1; ysvExt0 = ysb + 1; zsvExt0 = zsb + 1 }
                else if ((c1 and 0x02) == 0) { dxExt0 = dx0 - 1 - SQUISH_CONSTANT_3D; dyExt0 = dy0 + 1 - SQUISH_CONSTANT_3D; dzExt0 = dz0 - 1 - SQUISH_CONSTANT_3D; xsvExt0 = xsb + 1; ysvExt0 = ysb - 1; zsvExt0 = zsb + 1 }
                else { dxExt0 = dx0 - 1 - SQUISH_CONSTANT_3D; dyExt0 = dy0 - 1 - SQUISH_CONSTANT_3D; dzExt0 = dz0 + 1 - SQUISH_CONSTANT_3D; xsvExt0 = xsb + 1; ysvExt0 = ysb + 1; zsvExt0 = zsb - 1 }

                dxExt1 = dx0 - 2 * SQUISH_CONSTANT_3D; dyExt1 = dy0 - 2 * SQUISH_CONSTANT_3D; dzExt1 = dz0 - 2 * SQUISH_CONSTANT_3D
                xsvExt1 = xsb; ysvExt1 = ysb; zsvExt1 = zsb
                if ((c2 and 0x01) != 0) { dxExt1 -= 2.0; xsvExt1 += 2 }
                else if ((c2 and 0x02) != 0) { dyExt1 -= 2.0; ysvExt1 += 2 }
                else { dzExt1 -= 2.0; zsvExt1 += 2 }
            }

            val dx1 = dx0 - 1 - SQUISH_CONSTANT_3D
            val dy1 = dy0 - 0 - SQUISH_CONSTANT_3D
            val dz1 = dz0 - 0 - SQUISH_CONSTANT_3D
            var attn1 = 2.0 - dx1 * dx1 - dy1 * dy1 - dz1 * dz1
            if (attn1 > 0) { attn1 *= attn1; value += attn1 * attn1 * extrapolate(xsb + 1, ysb, zsb, dx1, dy1, dz1) }

            val dx2 = dx0 - 0 - SQUISH_CONSTANT_3D
            val dy2 = dy0 - 1 - SQUISH_CONSTANT_3D
            var attn2 = 2.0 - dx2 * dx2 - dy2 * dy2 - dz1 * dz1
            if (attn2 > 0) { attn2 *= attn2; value += attn2 * attn2 * extrapolate(xsb, ysb + 1, zsb, dx2, dy2, dz1) }

            val dz3 = dz0 - 1 - SQUISH_CONSTANT_3D
            var attn3 = 2.0 - dx2 * dx2 - dy1 * dy1 - dz3 * dz3
            if (attn3 > 0) { attn3 *= attn3; value += attn3 * attn3 * extrapolate(xsb, ysb, zsb + 1, dx2, dy1, dz3) }

            val dx4 = dx0 - 1 - 2 * SQUISH_CONSTANT_3D
            val dy4 = dy0 - 1 - 2 * SQUISH_CONSTANT_3D
            val dz4 = dz0 - 0 - 2 * SQUISH_CONSTANT_3D
            var attn4 = 2.0 - dx4 * dx4 - dy4 * dy4 - dz4 * dz4
            if (attn4 > 0) { attn4 *= attn4; value += attn4 * attn4 * extrapolate(xsb + 1, ysb + 1, zsb, dx4, dy4, dz4) }

            val dy5 = dy0 - 0 - 2 * SQUISH_CONSTANT_3D
            val dz5 = dz0 - 1 - 2 * SQUISH_CONSTANT_3D
            var attn5 = 2.0 - dx4 * dx4 - dy5 * dy5 - dz5 * dz5
            if (attn5 > 0) { attn5 *= attn5; value += attn5 * attn5 * extrapolate(xsb + 1, ysb, zsb + 1, dx4, dy5, dz5) }

            val dx6 = dx0 - 0 - 2 * SQUISH_CONSTANT_3D
            var attn6 = 2.0 - dx6 * dx6 - dy4 * dy4 - dz5 * dz5
            if (attn6 > 0) { attn6 *= attn6; value += attn6 * attn6 * extrapolate(xsb, ysb + 1, zsb + 1, dx6, dy4, dz5) }
        }

        var attnExt0 = 2.0 - dxExt0 * dxExt0 - dyExt0 * dyExt0 - dzExt0 * dzExt0
        if (attnExt0 > 0) { attnExt0 *= attnExt0; value += attnExt0 * attnExt0 * extrapolate(xsvExt0, ysvExt0, zsvExt0, dxExt0, dyExt0, dzExt0) }

        var attnExt1 = 2.0 - dxExt1 * dxExt1 - dyExt1 * dyExt1 - dzExt1 * dzExt1
        if (attnExt1 > 0) { attnExt1 *= attnExt1; value += attnExt1 * attnExt1 * extrapolate(xsvExt1, ysvExt1, zsvExt1, dxExt1, dyExt1, dzExt1) }

        return value / NORM_CONSTANT_3D
    }

    private fun extrapolate(xsb: Int, ysb: Int, zsb: Int, dx: Double, dy: Double, dz: Double): Double {
        val index = permGradIndex3D[perm[perm[xsb and 0xFF] + ysb and 0xFF] + zsb and 0xFF]
        return gradients3D[index].toDouble() * dx +
                gradients3D[index + 1].toDouble() * dy +
                gradients3D[index + 2].toDouble() * dz
    }

    private fun fastFloor(x: Double): Int {
        val xi = x.toInt()
        return if (x < xi) xi - 1 else xi
    }
}
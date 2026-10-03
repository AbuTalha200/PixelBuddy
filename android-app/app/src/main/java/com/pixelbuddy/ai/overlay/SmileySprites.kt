package com.pixelbuddy.ai.overlay

/**
 * Pixel-art data for both smileys. Faces are built on an 18x18 grid; the original 14x14
 * face sits at (OFFSET_ROW, OFFSET_COL) so hair, bows and hands have room around it.
 * All face coordinates below are "face-local" (0..13) and mirrored with `13 - col`.
 */
object SmileySprites {
    const val GRID = 18
    private const val OFFSET_ROW = 3
    private const val OFFSET_COL = 2

    // '0' = transparent, '1' = outline, '2' = face. Eyes are added per expression.
    private val BASE = arrayOf(
        "00011111111100",
        "01112222222110",
        "11222222222211",
        "12222222222221",
        "12222222222221",
        "12222222222221",
        "12222222222221",
        "12222222222221",
        "12212222221221",
        "12221222212221",
        "12222111122221",
        "11222222222211",
        "01112222222110",
        "00011111111100"
    )

    /** Two of these cover the female smiley's eyes when she gets shy (7 x 7). */
    val shyHand = arrayOf(
        ".ddddd.",
        "dhdhdhd",
        "dhdhdhd",
        "dhhhhhd",
        "dhhhhhd",
        ".dhhhd.",
        "..ddd.."
    )

    /** One big palm for the male smiley's facepalm (10 x 9). */
    val facepalmHand = arrayOf(
        ".dd.dd.dd.",
        "dhhdhhdhhd",
        "dhhdhhdhhd",
        "dhhdhhdhhd",
        "dhhhhhhhhd",
        "dhhhhhhhhd",
        "dhhhhhhhhd",
        ".dhhhhhhd.",
        "..dddddd.."
    )

    private val cache = HashMap<Pair<SmileyGender, FaceExpression>, Array<String>>()

    fun colorOf(pixel: Char): Int = when (pixel) {
        '1' -> 0xFF5F1016.toInt() // outline / features
        '2' -> 0xFFF14B49.toInt() // face
        '3' -> 0xFFFFA993.toInt() // eye shine
        '5' -> 0xFFFF9DB8.toInt() // blush
        '6' -> 0xFFFF6FB5.toInt() // bow
        '7' -> 0xFFA3195B.toInt() // bow outline
        '8' -> 0xFF4A2616.toInt() // hair
        'd' -> 0xFF8C4A2F.toInt() // hand outline
        'h' -> 0xFFFFD7B5.toInt() // hand skin
        else -> 0
    }

    fun face(gender: SmileyGender, expression: FaceExpression): Array<String> =
        cache.getOrPut(gender to expression) { build(gender, expression) }

    private fun build(gender: SmileyGender, expression: FaceExpression): Array<String> {
        val grid = Array(GRID) { CharArray(GRID) { '.' } }

        fun put(row: Int, col: Int, pixel: Char) {
            val r = row + OFFSET_ROW
            val c = col + OFFSET_COL
            if (r in 0 until GRID && c in 0 until GRID) grid[r][c] = pixel
        }

        fun mirrored(row: Int, col: Int, pixel: Char) {
            put(row, col, pixel)
            put(row, 13 - col, pixel)
        }

        fun clearMouth() {
            for (row in 8..10) for (col in 3..10) put(row, col, '2')
        }

        BASE.forEachIndexed { row, line ->
            line.forEachIndexed { col, pixel -> if (pixel != '0') put(row, col, pixel) }
        }

        when (expression) {
            FaceExpression.NORMAL -> {
                for (row in 5..6) {
                    mirrored(row, 2, '1')
                    mirrored(row, 3, '3')
                    mirrored(row, 4, '1')
                }
            }

            FaceExpression.BLINK -> {
                for (col in 2..4) mirrored(6, col, '1')
            }

            FaceExpression.SHY -> {
                // Happy closed eyes "^ ^" and a tiny embarrassed smile.
                mirrored(5, 3, '1')
                mirrored(6, 2, '1')
                mirrored(6, 4, '1')
                clearMouth()
                put(9, 5, '1')
                put(10, 6, '1')
                put(10, 7, '1')
                put(9, 8, '1')
            }

            FaceExpression.DISAPPOINTED -> {
                // Half-lidded eyes and a flat frown.
                for (col in 2..4) mirrored(5, col, '1')
                mirrored(6, 3, '1')
                clearMouth()
                for (col in 5..8) put(9, col, '1')
                mirrored(10, 4, '1')
            }
        }

        if (gender == SmileyGender.FEMALE) {
            mirrored(4, 1, '1') // lashes
            for (col in 2..3) mirrored(7, col, '5') // blush
            if (expression == FaceExpression.SHY) mirrored(7, 4, '5')

            // Bow, top right.
            for (col in intArrayOf(8, 9, 11, 12)) {
                put(-2, col, '7')
                put(0, col, '7')
            }
            put(-1, 8, '7')
            put(-1, 9, '6')
            put(-1, 10, '7')
            put(-1, 11, '6')
            put(-1, 12, '7')
        } else {
            // Eyebrows (worried when disappointed) and a short tuft of hair.
            if (expression == FaceExpression.DISAPPOINTED) {
                mirrored(3, 2, '1')
                mirrored(3, 3, '1')
                mirrored(2, 4, '1')
            } else {
                for (col in 2..4) mirrored(3, col, '1')
            }
            for (col in 5..8) put(-2, col, '8')
            for (col in 3..10) put(-1, col, '8')
        }

        return Array(GRID) { String(grid[it]) }
    }
}

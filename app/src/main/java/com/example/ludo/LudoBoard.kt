package com.example.ludo

/** The four seats, in clockwise order around the board. */
enum class LudoColor {
    RED,
    GREEN,
    YELLOW,
    BLUE;

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/** A square of the 15x15 board grid. Row 0 is the top edge, column 0 the left edge. */
data class Cell(val row: Int, val col: Int)

/** A point on the board grid in cell units, where the centre of cell (r, c) is (r + 0.5, c + 0.5). */
data class BoardPoint(val row: Float, val col: Float)

/**
 * The geometry of a standard Ludo board, and the mapping from a token's progress to a square.
 *
 * A token's position is a single number:
 *
 * ```
 *  0          in the yard, not yet in play
 *  1 .. 51    on the shared track, counted from that colour's own start square
 * 52 .. 56    in that colour's private home column
 * 57          home
 * ```
 *
 * Counting from each colour's own start square is what makes the four routes identical: the same
 * progress means the same distance travelled, whichever seat you are in.
 */
object LudoBoard {

    const val GRID = 15
    const val TRACK_LENGTH = 52
    const val TRACK_STEPS = 51
    const val HOME_COLUMN_LENGTH = 5
    const val TOKENS_PER_PLAYER = 4

    const val YARD = 0
    const val FIRST_HOME_COLUMN_PROGRESS = TRACK_STEPS + 1 // 52
    const val HOME = TRACK_STEPS + HOME_COLUMN_LENGTH + 1 // 57

    /** Where each colour joins the shared track. */
    val startIndices: Map<LudoColor, Int> = mapOf(
        LudoColor.RED to 0,
        LudoColor.GREEN to 13,
        LudoColor.YELLOW to 26,
        LudoColor.BLUE to 39
    )

    /** The four start squares plus the four starred squares: no capture happens on these. */
    val safeTrackIndices: Set<Int> = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    /** The 52 shared squares in clockwise order, starting at Red's start square. */
    val track: List<Cell> = buildList {
        for (col in 1..5) add(Cell(6, col))
        for (row in 5 downTo 0) add(Cell(row, 6))
        add(Cell(0, 7))
        for (row in 0..5) add(Cell(row, 8))
        for (col in 9..14) add(Cell(6, col))
        add(Cell(7, 14))
        for (col in 14 downTo 9) add(Cell(8, col))
        for (row in 9..14) add(Cell(row, 8))
        add(Cell(14, 7))
        for (row in 14 downTo 9) add(Cell(row, 6))
        for (col in 5 downTo 0) add(Cell(8, col))
        add(Cell(7, 0))
        add(Cell(6, 0))
    }

    /** The five private squares leading to the centre, outermost first. */
    val homeColumns: Map<LudoColor, List<Cell>> = mapOf(
        LudoColor.RED to (1..5).map { Cell(7, it) },
        LudoColor.GREEN to (1..5).map { Cell(it, 7) },
        LudoColor.YELLOW to (1..5).map { Cell(7, 14 - it) },
        LudoColor.BLUE to (1..5).map { Cell(14 - it, 7) }
    )

    /** Top-left corner of each 6x6 yard. */
    val yardOrigins: Map<LudoColor, Cell> = mapOf(
        LudoColor.RED to Cell(0, 0),
        LudoColor.GREEN to Cell(0, 9),
        LudoColor.YELLOW to Cell(9, 9),
        LudoColor.BLUE to Cell(9, 0)
    )

    /** The centre of the board, where finished tokens rest. */
    val centre = BoardPoint(7.5f, 7.5f)

    init {
        require(track.size == TRACK_LENGTH) { "the track must have $TRACK_LENGTH squares" }
    }

    fun startIndex(color: LudoColor): Int = startIndices.getValue(color)

    /** The track square a token of [color] stands on, or null when it is off the shared track. */
    fun trackIndexOf(color: LudoColor, progress: Int): Int? =
        if (progress in 1..TRACK_STEPS) {
            (startIndex(color) + progress - 1) % TRACK_LENGTH
        } else {
            null
        }

    fun isSafeProgress(color: LudoColor, progress: Int): Boolean {
        if (progress >= FIRST_HOME_COLUMN_PROGRESS) return true // private, nothing can reach it
        val index = trackIndexOf(color, progress) ?: return true // the yard
        return index in safeTrackIndices
    }

    fun isStartProgress(progress: Int): Boolean = progress == 1

    /** The grid square a token occupies, or null when it is in the yard or home. */
    fun cellOf(color: LudoColor, progress: Int): Cell? = when {
        progress in 1..TRACK_STEPS -> track[trackIndexOf(color, progress)!!]
        progress in FIRST_HOME_COLUMN_PROGRESS until HOME ->
            homeColumns.getValue(color)[progress - FIRST_HOME_COLUMN_PROGRESS]
        else -> null
    }

    /** The four resting spots inside a yard, in token order. */
    fun yardSlots(color: LudoColor): List<BoardPoint> {
        val origin = yardOrigins.getValue(color)
        return listOf(
            BoardPoint(origin.row + 1.5f, origin.col + 1.5f),
            BoardPoint(origin.row + 1.5f, origin.col + 3.5f),
            BoardPoint(origin.row + 3.5f, origin.col + 1.5f),
            BoardPoint(origin.row + 3.5f, origin.col + 3.5f)
        )
    }

    /** Where a finished token sits inside the centre triangle of its colour. */
    fun homeSlot(color: LudoColor, tokenIndex: Int): BoardPoint {
        val spread = (tokenIndex - 1.5f) * 0.34f
        return when (color) {
            LudoColor.RED -> BoardPoint(centre.row + spread, centre.col - 0.75f)
            LudoColor.GREEN -> BoardPoint(centre.row - 0.75f, centre.col + spread)
            LudoColor.YELLOW -> BoardPoint(centre.row + spread, centre.col + 0.75f)
            LudoColor.BLUE -> BoardPoint(centre.row + 0.75f, centre.col + spread)
        }
    }

    /** The exact spot a token is drawn at, yard and home included. */
    fun pointOf(color: LudoColor, tokenIndex: Int, progress: Int): BoardPoint = when (progress) {
        YARD -> yardSlots(color)[tokenIndex]
        HOME -> homeSlot(color, tokenIndex)
        else -> cellOf(color, progress)!!.let { BoardPoint(it.row + 0.5f, it.col + 0.5f) }
    }
}

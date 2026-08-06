package com.example.ludo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoBoardTest {

    @Test
    fun theTrackIsFiftyTwoDistinctSquaresInsideTheGrid() {
        assertEquals(52, LudoBoard.track.size)
        assertEquals(52, LudoBoard.track.toSet().size)
        LudoBoard.track.forEach { cell ->
            assertTrue("$cell is off the board", cell.row in 0 until LudoBoard.GRID)
            assertTrue("$cell is off the board", cell.col in 0 until LudoBoard.GRID)
        }
    }

    @Test
    fun theTrackIsAContinuousLoop() {
        val cells = LudoBoard.track
        var cornerTurns = 0
        cells.indices.forEach { index ->
            val here = cells[index]
            val next = cells[(index + 1) % cells.size]
            val rows = Math.abs(here.row - next.row)
            val cols = Math.abs(here.col - next.col)
            assertEquals("$here to $next is not one square", 1, maxOf(rows, cols))
            // Where two arms meet, the ring steps across the corner of the centre block.
            if (rows == 1 && cols == 1) cornerTurns++
        }
        assertEquals("the ring should turn the corner exactly four times", 4, cornerTurns)
    }

    @Test
    fun everyColourStartsBesideItsOwnYard() {
        assertEquals(Cell(6, 1), LudoBoard.track[LudoBoard.startIndex(LudoColor.RED)])
        assertEquals(Cell(1, 8), LudoBoard.track[LudoBoard.startIndex(LudoColor.GREEN)])
        assertEquals(Cell(8, 13), LudoBoard.track[LudoBoard.startIndex(LudoColor.YELLOW)])
        assertEquals(Cell(13, 6), LudoBoard.track[LudoBoard.startIndex(LudoColor.BLUE)])
    }

    @Test
    fun theStartSquaresAreEvenlySpacedAndSafe() {
        val starts = LudoColor.entries.map { LudoBoard.startIndex(it) }
        assertEquals(listOf(0, 13, 26, 39), starts)
        starts.forEach { assertTrue(it in LudoBoard.safeTrackIndices) }
        assertEquals(8, LudoBoard.safeTrackIndices.size)
    }

    @Test
    fun theLastTrackSquareLeadsStraightIntoTheHomeColumn() {
        LudoColor.entries.forEach { color ->
            val lastTrackCell = LudoBoard.cellOf(color, LudoBoard.TRACK_STEPS)!!
            val firstHomeCell = LudoBoard.homeColumns.getValue(color).first()
            val distance = Math.abs(lastTrackCell.row - firstHomeCell.row) +
                Math.abs(lastTrackCell.col - firstHomeCell.col)
            assertEquals("$color turns off at the wrong square", 1, distance)
        }
    }

    @Test
    fun theHomeColumnLeadsToTheCentre() {
        // The centre block is the 3x3 square in the middle; the home columns stop just outside it.
        val centreBlock = (6..8).flatMap { row -> (6..8).map { col -> Cell(row, col) } }.toSet()
        LudoColor.entries.forEach { color ->
            val column = LudoBoard.homeColumns.getValue(color)
            assertEquals(5, column.size)
            assertTrue("$color's home column runs into the centre", column.none { it in centreBlock })
            val innermost = column.last()
            val touchesCentre = centreBlock.any {
                Math.abs(it.row - innermost.row) + Math.abs(it.col - innermost.col) == 1
            }
            assertTrue("$color does not finish next to the centre", touchesCentre)
        }
    }

    @Test
    fun allFourRoutesAreTheSameLength() {
        LudoColor.entries.forEach { color ->
            val visited = (1..LudoBoard.HOME).mapNotNull { LudoBoard.cellOf(color, it) }
            // 51 shared squares plus 5 private ones; the 57th step is the centre itself.
            assertEquals("$color has the wrong route length", 56, visited.size)
            assertEquals("$color revisits a square", 56, visited.toSet().size)
        }
        assertEquals(57, LudoBoard.HOME)
    }

    @Test
    fun progressMapsOntoTheSharedTrackOnlyWhileOnIt() {
        assertNull(LudoBoard.trackIndexOf(LudoColor.RED, LudoBoard.YARD))
        assertEquals(0, LudoBoard.trackIndexOf(LudoColor.RED, 1))
        assertEquals(50, LudoBoard.trackIndexOf(LudoColor.RED, 51))
        assertNull(LudoBoard.trackIndexOf(LudoColor.RED, 52))
        assertNull(LudoBoard.trackIndexOf(LudoColor.RED, LudoBoard.HOME))

        // Green's route is the same, rotated by thirteen squares.
        assertEquals(13, LudoBoard.trackIndexOf(LudoColor.GREEN, 1))
        assertEquals(11, LudoBoard.trackIndexOf(LudoColor.GREEN, 51))
    }

    @Test
    fun colouredRoutesShareTheSameSquares() {
        // Red's fifteenth square is also somewhere on Green's route.
        val redCell = LudoBoard.cellOf(LudoColor.RED, 15)
        val greenReaches = (1..LudoBoard.TRACK_STEPS).map { LudoBoard.cellOf(LudoColor.GREEN, it) }
        assertTrue(redCell in greenReaches)

        // Home columns are private.
        val redHome = LudoBoard.homeColumns.getValue(LudoColor.RED).toSet()
        LudoColor.entries.filter { it != LudoColor.RED }.forEach { other ->
            val reachable = (1..LudoBoard.TRACK_STEPS).mapNotNull { LudoBoard.cellOf(other, it) }
            assertTrue("$other can stand in Red's home column", reachable.none { it in redHome })
        }
    }

    @Test
    fun safeSquaresAreRecognisedByProgress() {
        assertTrue(LudoBoard.isSafeProgress(LudoColor.RED, 1)) // own start
        assertTrue(LudoBoard.isSafeProgress(LudoColor.RED, 9)) // the star at index 8
        assertFalse(LudoBoard.isSafeProgress(LudoColor.RED, 2))
        assertTrue("the home column cannot be reached by anyone else", LudoBoard.isSafeProgress(LudoColor.RED, 53))
    }

    @Test
    fun yardsAndHomeSlotsStayInsideTheirOwnCorner() {
        LudoColor.entries.forEach { color ->
            val origin = LudoBoard.yardOrigins.getValue(color)
            LudoBoard.yardSlots(color).forEach { slot ->
                assertTrue(slot.row > origin.row && slot.row < origin.row + 6)
                assertTrue(slot.col > origin.col && slot.col < origin.col + 6)
            }
            (0 until LudoBoard.TOKENS_PER_PLAYER).forEach { index ->
                val point = LudoBoard.homeSlot(color, index)
                assertTrue("home slot escapes the centre", point.row > 6f && point.row < 9f)
                assertTrue("home slot escapes the centre", point.col > 6f && point.col < 9f)
            }
        }
    }
}

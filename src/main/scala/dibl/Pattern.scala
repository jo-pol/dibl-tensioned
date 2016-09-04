/*
 Copyright 2016 Jo Pol
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see http://www.gnu.org/licenses/gpl.html dibl
*/
package dibl

import dibl.Matrix.{countLinks, toAbsWithMargins, toRelSrcNodes}

import scala.collection.immutable.IndexedSeq
import scala.util.Try

object Pattern {

  def failureMessage(tried: Try[_]): String =
    s"<text><tspan x='2' y='14'>${tried.failed.get.getMessage}</tspan></text>"

  def apply(tileMatrix: String,
            tileType: String,
            groupId: String = "GF0",
            offsetX: Int = 80,
            offsetY: Int = 120
           ): String = {
    val lines = {
      val triedLines = Matrix.toMatrixLines(tileMatrix)
      if (triedLines.isFailure) return failureMessage(triedLines)
      triedLines.get
    }
    val tt = TileType(tileType)
    val tileRows = lines.length
    val tileCols = lines(0).length
    def toX(col: Int): Int = col * 10 + offsetX
    def toY(row: Int): Int = row * 10 + offsetY
    def stripMargins(m: M) = countLinks(m).slice(2, 2 + tileRows).map(_.slice(2, 2 + tileCols))

    def createDiagram(relative: M, absolute: M) = {

      val needColor: Seq[(Int, Int)] = {
        val m = stripMargins(absolute)
        m.indices.flatMap(row => m(row).indices.map(col => (row, col))).
          filter(t => m(t._1)(t._2) % 4 > 0)
      }

      def toColor(row: Int, col: Int): String = {
        val cell = tt.toTileIndices(row, col, tileRows, tileCols)
        val i = needColor.indexOf(cell) + 0f
        if (i < 0) "999999" else {
          val hue = i / needColor.size
          val brightness = 0.2f + 0.15f * (i % 3)
          hslToRgb(hue, 1f, brightness)
        }
      }

      def createNode(row: Int, col: Int) =
        s"""    <path
           |      d='m ${toX(col) + 2},${toY(row)} a 2,2 0 0 1 -2,2 2,2 0 0 1 -2,-2 2,2 0 0 1 2,-2 2,2 0 0 1 2,2 z'
           |      style='fill:#${toColor(row, col)};fill-opacity:0.85;stroke:none'
           |    />
           |""".stripMargin

      def createTwoIn(targetRow: Int, targetCol: Int): String =
        relative(targetRow)(targetCol).map { sourceNode =>
          val (r, c) = sourceNode
          val sourceRow = r + targetRow
          val sourceCol = c + targetCol
          s"""    <path
             |      style='stroke:#000;fill:none'
             |      d='M ${toX(sourceCol)},${toY(sourceRow)} ${toX(targetCol)},${toY(targetRow)}'
             |    />
             |""".stripMargin + (
            if (absolute(sourceRow+2)(sourceCol+2).nonEmpty) ""
            else createNode(sourceRow, sourceCol))
        }.mkString("")

      def forAllCells(createSvgObject: (Int, Int) => String): IndexedSeq[Char] =
        relative.indices.
          flatMap(row => relative(row).indices.
            filter(col => relative(row)(col).nonEmpty).
            flatMap(col => createSvgObject(row, col))
          )

      (forAllCells(createTwoIn) ++
        forAllCells(createNode)
        ).toArray.mkString("")
    }

    def clones: String = {
      val brickOffset = if (tileType == "bricks") tileCols * 5 else 0
      def cloneRows(row1: Int): String = {
        val row2 = row1 + tileRows * 10 // TODO refactor into TileType
        List.range(start = -(if (tileType == "bricks")brickOffset else 10*tileCols), end = 200, step = tileCols * 10).
          map(w => {
            clone(w, row1) + clone(w - brickOffset, row2)
          }).mkString("")
      }
      List.range(start = -tileRows * 10, end = 200, step = tileRows * 20)
        .map(h => cloneRows(h)).mkString("")
    }

    def clone(i: Int, j: Int): String =
      s"""    <use
         |      transform='translate(${i+95},${j+45})'
         |      xlink:href='#$groupId'
         |      style='stroke:#000;fill:none'
         |    />
         |""".stripMargin

    val options = Array(s"matrix=${lines.mkString("%0D")}", s"tiles=$tileType")
    val url = "https://d-bl.github.io/GroundForge/index.html"
    def createPatch(relative: M, absolute: M) =
      s"""
         |  <text style='font-family:Arial;font-size:11pt'>
         |   <tspan x='${offsetX - 15}' y='${offsetY - 50}'>$tileType; ${tileRows}x$tileCols; ${lines.mkString(",")}</tspan>
         |   <tspan x='${offsetX - 15}' y='${offsetY - 30}' style='fill:#008;'>
         |    <a xlink:href='$url?${options.mkString("&amp;")}'>pair/thread diagrams</a>
         |   </tspan>
         |  </text>
         |  <g id ="$groupId">
         |${createDiagram(relative, absolute)}
         |  </g>
         |  <g>
         |$clones
         |  </g>
         |""".stripMargin

    val triedSVG = for {
      relative <- toRelSrcNodes(tileMatrix)
      absolute <- toAbsWithMargins(relative, tileRows, tileCols)
    } yield createPatch(relative,absolute)

    triedSVG.getOrElse(failureMessage(triedSVG))
  }
}

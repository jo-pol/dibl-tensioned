/*
 Copyright 2015 Jo Pol
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
package dibl.fte

object Data {
  case class Cell(col: Int, value: Double)

  def apply(faces: Seq[Face], nodes: Map[String, Seq[TopoLink]], links: Seq[TopoLink]): Seq[Seq[Double]] = {

    def cell(link: TopoLink, value: Double) = Cell(links.indexOf(link), value)

    val cells1 = faces.map { face =>
      val left = face.leftArc.map(cell(_, 1))
      val right = face.rightArc.map(cell(_, -1))
      left ++ right
    }
    val cells2 = nodes.map { case (id, links) =>
      links.map(link => cell(link, value(id, link))) // TODO value * weight
    }.toSeq
    (cells1 ++ cells2)
      .map { cells => // TODO functional approach?
        val row = new Array[Double](links.size)
        cells.foreach(cell =>
          row(cell.col) = cell.value
        )
        row.toSeq
      }
  }

  private def value(nodeId: String, link: TopoLink) = {
    if (link.sourceId == nodeId) 1
    else -1
  }
}

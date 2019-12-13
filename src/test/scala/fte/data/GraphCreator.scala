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
package fte.data

import dibl.LinkProps.{ WhiteStart, threadLink }
import dibl.proto.TilesConfig
import dibl.{ Diagram, LinkProps, NewPairDiagram, NodeProps, ThreadDiagram }
import fte.layout.OneFormTorus

object GraphCreator {

  /**
    * @param urlQuery parameters for: https://d-bl.github.io/GroundForge/tiles?
    *                 For now the tile must be a checker tile and
    *                 the patch size must span 3 columns and 2 rows of checker tiles.
    *                 A simplified ascii-art view of a pattern definition:
    *                 +----+----+----+
    *                 |....|....|....|
    *                 |....|....|....|
    *                 +----+----+----+
    *                 |....|XXXX|....|
    *                 |....|XXXX|....|
    *                 +----+----+----+
    * @return The X's in the pattern definition are added to the returned graph.
    */
  def fromDiagram(urlQuery: String): Option[Graph] = {
    val config = TilesConfig(urlQuery)

//    implicit val diagram: Diagram = ThreadDiagram(NewPairDiagram.create(config))
//    implicit val scale: Int = 2
    implicit val diagram: Diagram = NewPairDiagram.create(config)
    implicit val scale: Int = 1

    val cols = config.patchWidth / 3
    val rows = config.patchHeight / 2
    val links = dropDuplicates(diagram.links.filter { link =>
      // The top and side tiles of a diagram may have irregularities along the outer edges.
      // So select links arriving in the center bottom checker tile.
      val target = diagram.node(link.target)
      val y = unScale(target.y)
      val x = unScale(target.x)
      y >= rows && x >= cols && x < cols * 2 && !link.withPin
    })
    val targets = links.map(_.target).distinct.zipWithIndex
    val graph = new Graph()

    // create each vertex on the torus once, all in a single column
    val vertexMap = targets.map { case (nodeNr, i) =>
      val t = diagram.node(nodeNr)
      t.id -> graph.createVertex(i)
    }.toMap

    // create edges of one tile
    links.foreach { link =>
      val source = diagram.node(link.source)
      val target = diagram.node(link.target)
      val (dx, dy) = deltas(link.isInstanceOf[WhiteStart], source, target)
      graph.createEdge(vertexMap(source.id), vertexMap(target.id), dx, dy)
    }
    println(vertexMap.toArray.sortBy(_._2.toString).mkString("\n"))
    println("edges " + graph.getEdges.toArray.sortBy(_.toString).mkString("; "))

    if (new OneFormTorus(graph).layout())
      Some(graph)
    else None
  }

  private def deltas(whiteStart: Boolean, source: NodeProps, target: NodeProps)
                    (implicit scale: Int): (Int, Int) = {
    (scale, source.instructions, whiteStart) match {
      // initial pair diagram
      case (1, _, _) => ((source.x - target.x).toInt, (source.y - target.y).toInt)
      // the left thread leaving a cross has a white start
      case (_, "cross", true) => (-1, 1)
      case (_, "cross", _) => (1, 1)
      // the right thread leaving a twist has a white start
      case (_, _, true) => (1, 1)
      case _ => (-1, 1)
    }
  }

  private def dropDuplicates(links: Seq[LinkProps])(implicit diagram: Diagram): Seq[LinkProps] = {
    // An example of potential problems in thread diagrams:
    // the 8 links in ascii-art graph ">==<" should be reduced to 4 links as "><".
    // This means recursively reconnect the sources of "<" links with the sources of "=" links.
    val duplicates: Set[(Int, Int)] = links
      .map(l => l.target -> l.source)
      .groupBy(identity)
      .values.filter(_.size > 1)
      .flatten.toSet
    links
      .filter(l => !duplicates.contains(l.target -> l.source))
      .map(reconnect(_)(duplicates.toMap))
  }

  private def reconnect(link: LinkProps)(implicit duplicateSourcesByTarget: Map[Int, Int]): LinkProps = {
    duplicateSourcesByTarget.keySet.find(_ == link.source).map(target =>
      reconnect(threadLink(
        source = duplicateSourcesByTarget(target),
        target = link.target,
        threadNr = 0, // don't care
        whiteStart = link.isInstanceOf[WhiteStart]
      ))
    ).getOrElse(link)
  }

  /** Revert [[NewPairDiagram]].toPoint
    *
    * @param i     value for x or y
    * @param scale value 1 or factor to also undo [[ThreadDiagram]].scale
    * @return x/y on canvas reduced to row/col number in the input
    */
  private def unScale(i: Double)(implicit scale: Int): Int = {
    i / scale / 15 - 2
    }.toInt
}

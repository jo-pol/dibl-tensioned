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

import dibl.LinkProps.WhiteStart

case class TopoLink(sourceId: String, targetId: String, isLeftOfTarget: Boolean, isLeftOfSource: Boolean) {
  val isRightOfTarget: Boolean = !isLeftOfTarget
  val isRightOfSource: Boolean = !isLeftOfSource

  override def toString: String = s"$sourceId,$targetId,$isLeftOfTarget,$isLeftOfSource"
    .replaceAll("(rue|alse)", "")
}

object TopoLink {

  /** reduces diagram info of a tile to topological info embedded on a flat torus */
  def simplify(linksInOneTile: Seq[LinkProps])
              (implicit diagram: Diagram): Seq[TopoLink] = {
    implicit val topoLinks: Seq[LinkProps] = linksInOneTile
    linksInOneTile.map { link =>
      TopoLink(sourceOf(link).id, targetOf(link).id, isLeftOfTarget(link), isLeftOfSource(link))
    }
  }

  private def isLeftOfTarget(link: LinkProps)
                            (implicit diagram: Diagram,
                             linksInTile: Seq[LinkProps]
                            ): Boolean = {
    def isSiblingAtTarget(other: LinkProps) = {
      sourceOf(other).id != sourceOf(link).id &&
        targetOf(other).id == targetOf(link).id
    }

    def isLeftPairOfTarget = {
      val otherX: Double = linksInTile
        .find(isSiblingAtTarget) // TODO relatively expensive lookup for large matrices
        .map(other => sourceOf(other).x)
        .getOrElse(0)
      sourceOf(link).x < otherX
    }

    isLeft(
      targetOf(link).instructions,
      link.isInstanceOf[WhiteStart],
      isLeftPairOfTarget _
    )
  }

  private def isLeftOfSource(link: LinkProps)
                            (implicit diagram: Diagram,
                             linksInTile: Seq[LinkProps]
                            ): Boolean = {
    def isSiblingAtSource(other: LinkProps) = {
      targetOf(other).id != targetOf(link).id &&
        sourceOf(other).id == sourceOf(link).id
    }

    def isLeftPairAtSource = {
      val otherX: Double = linksInTile
        .find(isSiblingAtSource) // TODO relatively expensive lookup for large matrices
        .map(other => targetOf(other).x)
        .getOrElse(0)
      targetOf(link).x < otherX
    }

    isLeft(
      sourceOf(link).instructions,
      link.isInstanceOf[WhiteStart],
      isLeftPairAtSource _
    )
  }

  private def isLeft(sourceInstructions: String,
                     isWhiteStart: Boolean,
                     isLeftPair: () => Boolean
                    ) = {
    (sourceInstructions, isWhiteStart) match {
      /*    cross   twist
       *    \  /    \   /
       *     \        /
       *    / \     /  \
       */
      case ("cross", true) => true
      case ("cross", false) => false
      case ("twist", false) => true
      case ("twist", true) => false
      case _ => isLeftPair()
    }
  }

  def sourceOf(l: LinkProps)
              (implicit diagram: Diagram): NodeProps = {
    diagram.node(l.source)
  }

  def targetOf(l: LinkProps)
              (implicit diagram: Diagram): NodeProps = {
    diagram.node(l.target)
  }
}

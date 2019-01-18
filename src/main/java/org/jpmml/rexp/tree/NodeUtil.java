/*
 * Copyright (c) 2019 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp.tree;

import org.dmg.pmml.tree.ComplexNode;
import org.dmg.pmml.tree.Node;

public class NodeUtil {

	private NodeUtil(){
	}

	static
	public ComplexNode toComplexNode(Node node){
		ComplexNode result = new ComplexNode()
			.setId(node.getId())
			.setScore(node.getScore())
			.setRecordCount(node.getRecordCount())
			.setDefaultChild(node.getDefaultChild())
			.setPredicate(node.getPredicate());

		if(node.hasNodes()){
			(result.getNodes()).addAll(node.getNodes());
		} // End if

		if(node.hasScoreDistributions()){
			(result.getScoreDistributions()).addAll(node.getScoreDistributions());
		}

		return result;
	}
}
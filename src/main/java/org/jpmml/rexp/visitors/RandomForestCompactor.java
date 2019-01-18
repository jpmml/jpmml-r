/*
 * Copyright (c) 2018 Villu Ruusmann
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
package org.jpmml.rexp.visitors;

import java.util.List;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.HasFieldReference;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.visitors.AbstractTreeModelTransformer;

public class RandomForestCompactor extends AbstractTreeModelTransformer {

	@Override
	public void enterNode(Node node){
		String id = node.getId();
		Object score = node.getScore();

		if(id == null){
			throw new IllegalArgumentException();
		} // End if

		if(node.hasNodes()){
			List<Node> children = node.getNodes();

			if(children.size() != 2 || score != null){
				throw new IllegalArgumentException();
			}

			Node firstChild = children.get(0);
			Node secondChild = children.get(1);

			Predicate firstPredicate = firstChild.getPredicate();
			Predicate secondPredicate = secondChild.getPredicate();

			checkFieldReference(firstPredicate, secondPredicate);

			boolean update = isDefinedField((HasFieldReference<?>)firstPredicate);

			if(hasOperator(firstPredicate, SimplePredicate.Operator.EQUAL) && hasOperator(secondPredicate, SimplePredicate.Operator.EQUAL)){
				// Ignored
			} else

			if(hasOperator(firstPredicate, SimplePredicate.Operator.LESS_OR_EQUAL) && hasOperator(secondPredicate, SimplePredicate.Operator.GREATER_THAN)){
				update = true;
			} else

			if(hasOperator(firstPredicate, SimplePredicate.Operator.EQUAL) && hasBooleanOperator(secondPredicate, SimpleSetPredicate.BooleanOperator.IS_IN)){
				// Ignored
			} else

			if(hasBooleanOperator(firstPredicate, SimpleSetPredicate.BooleanOperator.IS_IN) && hasOperator(secondPredicate, SimplePredicate.Operator.EQUAL)){

				if(update){
					children = swapChildren(node);

					firstChild = children.get(0);
					secondChild = children.get(1);
				}
			} else

			if(hasBooleanOperator(firstPredicate, SimpleSetPredicate.BooleanOperator.IS_IN) && hasBooleanOperator(secondPredicate, SimpleSetPredicate.BooleanOperator.IS_IN)){
				// Ignored
			} else

			{
				throw new IllegalArgumentException();
			} // End if

			if(update){
				secondChild.setPredicate(new True());
			}
		} else

		{
			if(score == null){
				throw new IllegalArgumentException();
			}
		}

		node.setId(null);
	}

	@Override
	public void exitNode(Node node){
		Predicate predicate = node.getPredicate();

		if(predicate instanceof True){
			Node parentNode = getParentNode();

			if(parentNode == null){
				return;
			}

			initScore(parentNode, node);
			replaceChildWithGrandchildren(parentNode, node);
		}
	}

	@Override
	public void enterTreeModel(TreeModel treeModel){
		TreeModel.NoTrueChildStrategy noTrueChildStrategy = treeModel.getNoTrueChildStrategy();
		TreeModel.SplitCharacteristic splitCharacteristic = treeModel.getSplitCharacteristic();

		if(!(TreeModel.NoTrueChildStrategy.RETURN_NULL_PREDICTION).equals(noTrueChildStrategy) || !(TreeModel.SplitCharacteristic.BINARY_SPLIT).equals(splitCharacteristic)){
			throw new IllegalArgumentException();
		}
	}

	@Override
	public void exitTreeModel(TreeModel treeModel){
		treeModel
			.setNoTrueChildStrategy(TreeModel.NoTrueChildStrategy.RETURN_LAST_PREDICTION)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.MULTI_SPLIT);
	}

	private boolean isDefinedField(HasFieldReference<?> hasFieldReference){
		FieldName name = hasFieldReference.getField();

		Node ancestorNode = getAncestorNode(node -> hasFieldReference(node.getPredicate(), name));

		return (ancestorNode != null);
	}
}
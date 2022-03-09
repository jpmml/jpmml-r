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

import org.dmg.pmml.HasFieldReference;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.visitors.AbstractTreeModelTransformer;
import org.jpmml.model.UnsupportedAttributeException;
import org.jpmml.model.UnsupportedElementException;

public class RandomForestCompactor extends AbstractTreeModelTransformer {

	@Override
	public void enterNode(Node node){
		Object id = node.getId();
		Object score = node.getScore();

		if(id == null){
			throw new UnsupportedElementException(node);
		} // End if

		if(node.hasNodes()){
			List<Node> children = node.getNodes();

			if(score != null || children.size() != 2){
				throw new UnsupportedElementException(node);
			}

			Node firstChild = children.get(0);
			Node secondChild = children.get(1);

			Predicate firstPredicate = firstChild.requirePredicate();
			Predicate secondPredicate = secondChild.requirePredicate();

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
				throw new UnsupportedElementException(node);
			} // End if

			if(update){
				secondChild.setPredicate(True.INSTANCE);
			}
		} else

		{
			if(score == null){
				throw new UnsupportedElementException(node);
			}
		}

		node.setId(null);
	}

	@Override
	public void exitNode(Node node){
		Predicate predicate = node.requirePredicate();

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
		super.enterTreeModel(treeModel);

		TreeModel.NoTrueChildStrategy noTrueChildStrategy = treeModel.getNoTrueChildStrategy();
		if(noTrueChildStrategy != TreeModel.NoTrueChildStrategy.RETURN_NULL_PREDICTION){
			throw new UnsupportedAttributeException(treeModel, noTrueChildStrategy);
		}

		TreeModel.SplitCharacteristic splitCharacteristic = treeModel.getSplitCharacteristic();
		if(splitCharacteristic != TreeModel.SplitCharacteristic.BINARY_SPLIT){
			throw new UnsupportedAttributeException(treeModel, splitCharacteristic);
		}

		treeModel
			.setNoTrueChildStrategy(TreeModel.NoTrueChildStrategy.RETURN_LAST_PREDICTION)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.MULTI_SPLIT);
	}

	@Override
	public void exitTreeModel(TreeModel treeModel){
		super.exitTreeModel(treeModel);
	}

	private boolean isDefinedField(HasFieldReference<?> hasFieldReference){
		String name = hasFieldReference.requireField();

		Node ancestorNode = getAncestorNode(node -> hasFieldReference(node.requirePredicate(), name));

		return (ancestorNode != null);
	}
}
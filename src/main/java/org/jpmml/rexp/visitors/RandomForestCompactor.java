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

import java.util.Deque;
import java.util.List;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.HasFieldReference;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.VisitorAction;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.model.visitors.AbstractVisitor;

public class RandomForestCompactor extends AbstractVisitor {

	@Override
	public void pushParent(PMMLObject object){
		super.pushParent(object);

		if(object instanceof Node){
			handleNodePush((Node)object);
		}
	}

	@Override
	public PMMLObject popParent(){
		PMMLObject object = super.popParent();

		if(object instanceof Node){
			handleNodePop((Node)object);
		}

		return object;
	}

	@Override
	public VisitorAction visit(TreeModel treeModel){
		TreeModel.NoTrueChildStrategy noTrueChildStrategy = treeModel.getNoTrueChildStrategy();
		TreeModel.SplitCharacteristic splitCharacteristic = treeModel.getSplitCharacteristic();

		if(!(TreeModel.NoTrueChildStrategy.RETURN_NULL_PREDICTION).equals(noTrueChildStrategy) || !(TreeModel.SplitCharacteristic.BINARY_SPLIT).equals(splitCharacteristic)){
			throw new IllegalArgumentException();
		}

		treeModel
			.setNoTrueChildStrategy(TreeModel.NoTrueChildStrategy.RETURN_LAST_PREDICTION)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.MULTI_SPLIT);

		return super.visit(treeModel);
	}

	private <P extends Predicate & HasFieldReference<P>> void handleNodePush(Node node){
		String id = node.getId();
		String score = node.getScore();

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

			P firstPredicate = (P)firstChild.getPredicate();
			P secondPredicate = (P)secondChild.getPredicate();

			if(!(firstPredicate.getField()).equals(secondPredicate.getField())){
				throw new IllegalArgumentException();
			}

			boolean update = isDefined(firstPredicate.getField());

			if((firstPredicate instanceof SimplePredicate) && (secondPredicate instanceof SimplePredicate)){
				SimplePredicate.Operator firstOperator = ((SimplePredicate)firstPredicate).getOperator();
				SimplePredicate.Operator secondOperator = ((SimplePredicate)secondPredicate).getOperator();

				if((SimplePredicate.Operator.LESS_OR_EQUAL).equals(firstOperator) && (SimplePredicate.Operator.GREATER_THAN).equals(secondOperator)){
					update = true;
				} else

				{
					checkOperator((SimplePredicate)firstPredicate, SimplePredicate.Operator.EQUAL);
					checkOperator((SimplePredicate)secondPredicate, SimplePredicate.Operator.EQUAL);
				}
			} else

			if((firstPredicate instanceof SimplePredicate) && (secondPredicate instanceof SimpleSetPredicate)){
				checkOperator((SimplePredicate)firstPredicate, SimplePredicate.Operator.EQUAL);
				checkBooleanOperator((SimpleSetPredicate)secondPredicate, SimpleSetPredicate.BooleanOperator.IS_IN);
			} else

			if((firstPredicate instanceof SimpleSetPredicate) && (secondPredicate instanceof SimplePredicate)){
				checkBooleanOperator((SimpleSetPredicate)firstPredicate, SimpleSetPredicate.BooleanOperator.IS_IN);
				checkOperator((SimplePredicate)secondPredicate, SimplePredicate.Operator.EQUAL);

				if(update){
					children.remove(0);
					children.add(1, firstChild);

					firstChild = children.get(0);
					secondChild = children.get(1);
				}
			} else

			if((firstPredicate instanceof SimpleSetPredicate) && (secondPredicate instanceof SimpleSetPredicate)){
				checkBooleanOperator((SimpleSetPredicate)firstPredicate, SimpleSetPredicate.BooleanOperator.IS_IN);
				checkBooleanOperator((SimpleSetPredicate)secondPredicate, SimpleSetPredicate.BooleanOperator.IS_IN);
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

	private void handleNodePop(Node node){
		String score = node.getScore();
		Predicate predicate = node.getPredicate();

		if(predicate instanceof True){
			Node parentNode = getParentNode();

			if(parentNode == null){
				return;
			}

			String parentScore = parentNode.getScore();
			if(parentScore != null){
				throw new IllegalArgumentException();
			}

			parentNode.setScore(score);

			List<Node> parentChildren = parentNode.getNodes();

			boolean success = parentChildren.remove(node);
			if(!success){
				throw new IllegalArgumentException();
			} // End if

			if(node.hasNodes()){
				List<Node> children = node.getNodes();

				parentChildren.addAll(children);
			}
		}
	}

	private boolean isDefined(FieldName name){
		Deque<PMMLObject> parents = getParents();

		for(PMMLObject parent : parents){

			if(parent instanceof Node){
				Node node = (Node)parent;

				Predicate predicate = node.getPredicate();
				if(predicate instanceof HasFieldReference){
					HasFieldReference<?> hasFieldReference = (HasFieldReference<?>)predicate;

					if((name).equals(hasFieldReference.getField())){
						return true;
					}
				}
			} else

			{
				return false;
			}
		}

		return false;
	}

	private Node getParentNode(){
		Deque<PMMLObject> parents = getParents();

		PMMLObject parent = parents.peekFirst();
		if(parent instanceof Node){
			return (Node)parent;
		}

		return null;
	}

	static
	private void checkOperator(SimplePredicate simplePredicate, SimplePredicate.Operator operator){

		if(!(operator).equals(simplePredicate.getOperator())){
			throw new IllegalArgumentException();
		}
	}

	static
	public void checkBooleanOperator(SimpleSetPredicate simpleSetPredicate, SimpleSetPredicate.BooleanOperator booleanOperator){

		if(!(booleanOperator).equals(simpleSetPredicate.getBooleanOperator())){
			throw new IllegalArgumentException();
		}
	}
}
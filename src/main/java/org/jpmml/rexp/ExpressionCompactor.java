/*
 * Copyright (c) 2016 Villu Ruusmann
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
package org.jpmml.rexp;

import java.util.List;
import java.util.ListIterator;

import org.dmg.pmml.Apply;
import org.dmg.pmml.Expression;
import org.dmg.pmml.VisitorAction;
import org.jpmml.model.visitors.AbstractVisitor;

public class ExpressionCompactor extends AbstractVisitor {

	@Override
	public VisitorAction visit(Apply apply){
		String function = apply.getFunction();

		switch(function){
			case "and":
			case "or":
				inlineLogicalExpressions(apply);
				break;
			case "not":
				negateExpression(apply);
				break;
			default:
				break;
		}

		return VisitorAction.CONTINUE;
	}

	static
	private void inlineLogicalExpressions(Apply apply){
		String function = apply.getFunction();

		List<Expression> expressions = apply.getExpressions();
		for(ListIterator<Expression> expressionIt = expressions.listIterator(); expressionIt.hasNext(); ){
			Expression expression = expressionIt.next();

			if(expression instanceof Apply){
				Apply nestedApply = (Apply)expression;

				if((function).equals(nestedApply.getFunction())){
					expressionIt.remove();

					// Deep inline
					inlineLogicalExpressions(nestedApply);

					List<Expression> nestedExpressions = nestedApply.getExpressions();
					for(Expression nestedExpression : nestedExpressions){
						expressionIt.add(nestedExpression);
					}
				}
			}
		}
	}

	static
	private void negateExpression(Apply apply){
		List<Expression> expressions = apply.getExpressions();

		if(expressions.size() != 1){
			throw new IllegalArgumentException();
		}

		ListIterator<Expression> expressionIt = expressions.listIterator();

		Expression expression = expressionIt.next();

		if(expression instanceof Apply){
			Apply nestedApply = (Apply)expression;

			String negatedFunction = negate(nestedApply.getFunction());
			if(negatedFunction != null){
				apply.setFunction(negatedFunction);

				expressionIt.remove();

				List<Expression> nestedExpressions = nestedApply.getExpressions();
				for(Expression nestedExpression : nestedExpressions){
					expressionIt.add(nestedExpression);
				}
			}
		}
	}

	static
	private String negate(String function){

		switch(function){
			case "equal":
				return "notEqual";
			case "isMissing":
				return "isNotMissing";
			default:
				return null;
		}
	}
}
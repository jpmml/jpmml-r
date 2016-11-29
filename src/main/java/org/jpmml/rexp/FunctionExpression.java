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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dmg.pmml.Expression;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Visitor;
import org.dmg.pmml.VisitorAction;

public class FunctionExpression extends Expression {

	private String function = null;

	private List<Argument> arguments = null;


	public FunctionExpression(String function, List<Argument> arguments){
		this.function = function;
		this.arguments = arguments;
	}

	public Expression getExpression(String tag){

		if(tag == null){
			throw new NullPointerException();
		} // End if

		if(this.arguments == null){
			throw new IllegalArgumentException(tag);
		}

		List<Argument> arguments = this.arguments;
		for(Argument argument : arguments){

			if((tag).equals(argument.getTag())){
				return argument.getExpression();
			}
		}

		throw new IllegalArgumentException(tag);
	}

	public Expression getExpression(int index){

		if(this.arguments == null){
			throw new ArrayIndexOutOfBoundsException(index);
		}

		Argument argument = this.arguments.get(index);

		return argument.getExpression();
	}

	public String getFunction(){
		return this.function;
	}

	public FunctionExpression setFunction(String function){
		this.function = function;

		return this;
	}

	public List<Argument> getArguments(){

		if(this.arguments == null){
			this.arguments = new ArrayList<>();
		}

		return this.arguments;
	}

	public boolean hasArguments(){
		return (this.arguments != null && this.arguments.size() > 0);
	}

	public FunctionExpression addArguments(Argument... arguments){
		getArguments().addAll(Arrays.asList(arguments));

		return this;
	}

	@Override
	public VisitorAction accept(Visitor visitor){
		VisitorAction status = VisitorAction.CONTINUE;

		if((status == VisitorAction.CONTINUE) && hasArguments()){
			List<Argument> arguments = getArguments();

			for(Argument argument : arguments){
				status = PMMLObject.traverse(visitor, argument.getExpression());

				if(status != VisitorAction.CONTINUE){
					break;
				}
			}
		}

		if(status == VisitorAction.TERMINATE){
			return VisitorAction.TERMINATE;
		}

		return VisitorAction.CONTINUE;
	}

	static
	public class Argument {

		private String tag = null;

		private Expression expression = null;


		public Argument(Expression expression){
			setExpression(expression);
		}

		public Argument(String tag, Expression expression){
			setTag(tag);
			setExpression(expression);
		}

		public String getTag(){
			return this.tag;
		}

		private void setTag(String tag){
			this.tag = tag;
		}

		public boolean hasTag(){
			return (this.tag != null);
		}

		public Expression getExpression(){
			return this.expression;
		}

		private void setExpression(Expression expression){
			this.expression = expression;
		}
	}
}
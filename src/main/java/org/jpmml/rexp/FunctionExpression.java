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
import java.util.Objects;
import java.util.Set;

import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Visitor;
import org.dmg.pmml.VisitorAction;
import org.jpmml.model.visitors.ActiveFieldFinder;

public class FunctionExpression extends Expression {

	private String namespace = null;

	private String function = null;

	private List<Argument> arguments = null;


	public FunctionExpression(String function, List<Argument> arguments){
		this(null, function, arguments);
	}

	public FunctionExpression(String namespace, String function, List<Argument> arguments){
		this.namespace = namespace;
		this.function = function;
		this.arguments = arguments;
	}

	public boolean hasId(String namespace, String function){
		return (Objects.equals(this.namespace, namespace) || Objects.equals(this.namespace, null)) && Objects.equals(this.function, function);
	}

	public Argument getArgument(String tag, int index){

		if(tag != null){

			try {
				return getArgument(tag);
			} catch(IllegalArgumentException iae){
				// Ignored
			}
		}

		return getArgument(index);
	}

	public Argument getArgument(String tag){

		if(tag == null){
			throw new NullPointerException();
		} // End if

		if(this.arguments == null){
			throw new IllegalArgumentException(tag);
		}

		List<Argument> arguments = this.arguments;
		for(Argument argument : arguments){

			if((tag).equals(argument.getTag())){
				return argument;
			}
		}

		throw new IllegalArgumentException(tag);
	}

	public Argument getArgument(int index){

		if(this.arguments == null){
			throw new ArrayIndexOutOfBoundsException(index);
		}

		return this.arguments.get(index);
	}

	public String getNamespace(){
		return this.namespace;
	}

	public FunctionExpression setNamespace(String namespace){
		this.namespace = namespace;

		return this;
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
		VisitorAction status = visitor.visit(this);

		if(status == VisitorAction.CONTINUE){
			visitor.pushParent(this);

			if(hasArguments()){
				List<Argument> arguments = getArguments();

				for(Argument argument : arguments){
					status = PMMLObject.traverse(visitor, argument.getExpression());

					if(status != VisitorAction.CONTINUE){
						break;
					}
				}
			}

			visitor.popParent();
		} // End if

		if(status == VisitorAction.TERMINATE){
			return VisitorAction.TERMINATE;
		}

		return VisitorAction.CONTINUE;
	}

	static
	public class Argument {

		private Token begin = null;

		private Token end = null;

		private String tag = null;

		private Expression expression = null;


		public Argument(Expression expression){
			this(null, expression);
		}

		public Argument(String tag, Expression expression){
			setTag(tag);
			setExpression(expression);
		}

		public String format(){
			Token begin = getBegin();
			Token end = getEnd();

			if(begin == null || end == null){
				throw new IllegalStateException();
			}

			return format(begin, end);
		}

		public String formatExpression(){
			Token begin = getBegin();
			Token end = getEnd();

			if(begin == null || end == null){
				throw new IllegalStateException();
			}

			switch(begin.next.kind){
				case ExpressionTranslatorConstants.IDENTIFIER:
				case ExpressionTranslatorConstants.STRING:

					switch(begin.next.next.kind){
						case ExpressionTranslatorConstants.ASSIGN:
							begin = begin.next.next;
							break;
						default:
							break;
					}
					break;
				default:
					break;
			}

			return format(begin, end);
		}

		public Set<FieldName> getFieldNames(){
			Expression expression = getExpression();

			ActiveFieldFinder activeFieldFinder = new ActiveFieldFinder();
			activeFieldFinder.applyTo(expression);

			return activeFieldFinder.getFieldNames();
		}

		Token getBegin(){
			return this.begin;
		}

		void setBegin(Token begin){
			this.begin = begin;
		}

		Token getEnd(){
			return this.end;
		}

		void setEnd(Token end){
			this.end = end;
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

		static
		private String format(Token begin, Token end){
			StringBuilder sb = new StringBuilder();

			for(Token token = begin.next; token != end; token = token.next){
				int pos = sb.length();

				if(token != begin.next){

					for(Token specialToken = token.specialToken; specialToken != null; specialToken = specialToken.specialToken){
						sb.insert(pos, specialToken.image);
					}
				}

				sb.append(token.image);
			}

			return sb.toString();
		}
	}
}
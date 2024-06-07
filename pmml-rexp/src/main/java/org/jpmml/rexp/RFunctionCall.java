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

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public class RFunctionCall extends RPair {

	public RFunctionCall(RExp tag, RExp value, RPair arguments, RPair attributes){
		super(tag, value, attributes);

		setNext(arguments);
	}

	public boolean hasValue(String string){
		RString value = (RString)getValue();

		return Objects.equals(string, value.getValue());
	}

	public Iterator<RPair> arguments(){
		Iterator<RPair> result = new Iterator<RPair>(){

			private RPair next = getNext();


			@Override
			public boolean hasNext(){
				return (this.next != null);
			}

			@Override
			public RPair next(){
				RPair result = this.next;

				if(result == null){
					throw new NoSuchElementException();
				}

				this.next = result.getNext();

				return result;
			}
		};

		return result;
	}

	public Iterator<RExp> argumentValues(){
		Iterator<RExp> result = new Iterator<RExp>(){

			private Iterator<RPair> argumentIt = arguments();


			@Override
			public boolean hasNext(){
				return this.argumentIt.hasNext();
			}

			@Override
			public RExp next(){
				RPair argument = this.argumentIt.next();

				return argument.getValue();
			}
		};

		return result;
	}

	public String toTreeString(String indent){
		StringBuilder sb = new StringBuilder();

		// Operator
		RString value = (RString)getValue();

		sb.append(indent).append(value.getValue());

		indent += "\t";

		// Operands
		for(Iterator<RExp> it = argumentValues(); it.hasNext(); ){
			RExp argValue = it.next();

			sb.append("\n");

			if(argValue instanceof RString){
				RString string = (RString)argValue;

				sb.append(indent).append(string.getValue()).append(" // ").append(argValue.getClass().getSimpleName());
			} else

			if(argValue instanceof RVector){
				RVector<?> vector = (RVector<?>)argValue;

				sb.append(indent).append(vector.asScalar()).append(" // ").append(argValue.getClass().getSimpleName());
			} else

			if(argValue instanceof RFunctionCall){
				RFunctionCall functionCall = (RFunctionCall)argValue;

				sb.append(functionCall.toTreeString(indent));
			} else

			{
				throw new IllegalArgumentException(argValue.getClass().getName());
			}
		}

		return sb.toString();
	}
}
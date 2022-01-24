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

public class RFunctionCall extends RExp {

	private RExp tag = null;

	private RExp function = null;

	private RExp arguments = null;


	public RFunctionCall(RExp tag, RExp function, RExp arguments, RPair attributes){
		super(attributes);

		setTag(tag);
		setFunction(function);
		setArguments(arguments);
	}

	public RExp getTag(){
		return this.tag;
	}

	private void setTag(RExp tag){
		this.tag = tag;
	}

	public RExp getFunction(){
		return this.function;
	}

	private void setFunction(RExp function){
		this.function = function;
	}

	public RExp getArguments(){
		return this.arguments;
	}

	private void setArguments(RExp arguments){
		this.arguments = arguments;
	}
}
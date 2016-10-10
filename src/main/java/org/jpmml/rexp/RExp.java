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

abstract
public class RExp {

	private RPair attributes = null;


	public RExp(RPair attributes){
		setAttributes(attributes);
	}

	public RStringVector names(){
		return (RStringVector)getAttributeValue("names");
	}

	public RIntegerVector dim(){
		return (RIntegerVector)getAttributeValue("dim");
	}

	public RExp getAttributeValue(String name){
		return getAttributeValue(name, false);
	}

	public RExp getAttributeValue(String name, boolean optional){
		RPair attribute = getAttribute(name);
		if(attribute != null){
			return attribute.getValue();
		}

		if(optional){
			return null;
		}

		throw new IllegalArgumentException(name);
	}

	public RPair getAttribute(String tag){
		RPair attribute = getAttributes();
		if(attribute == null){
			throw new IllegalArgumentException();
		}

		while(attribute != null){

			if(attribute.tagEquals(tag)){
				return attribute;
			}

			attribute = attribute.getNext();
		}

		return null;
	}

	public RPair getAttributes(){
		return this.attributes;
	}

	private void setAttributes(RPair attributes){
		this.attributes = attributes;
	}
}
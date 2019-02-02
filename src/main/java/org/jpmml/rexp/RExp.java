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
		return getStringAttribute("names");
	}

	public RIntegerVector dim(){
		return getIntegerAttribute("dim");
	}

	public RStringVector dimnames(int index){
		RGenericVector dimnames = getGenericAttribute("dimnames");

		return (RStringVector)dimnames.getValue(index);
	}

	public RExp getAttribute(String name){
		return findAttribute(name, false);
	}

	public RExp getAttribute(String name, boolean optional){
		return findAttribute(name, optional);
	}

	public RBooleanVector getBooleanAttribute(String name){
		return (RBooleanVector)getAttribute(name);
	}

	public RBooleanVector getBooleanAttribute(String name, boolean optional){
		return (RBooleanVector)getAttribute(name, optional);
	}

	public RDoubleVector getDoubleAttribute(String name){
		return (RDoubleVector)getAttribute(name);
	}

	public RDoubleVector getDoubleAttribute(String name, boolean optional){
		return (RDoubleVector)getAttribute(name, optional);
	}

	public RGenericVector getGenericAttribute(String name){
		return (RGenericVector)getAttribute(name);
	}

	public RGenericVector getGenericAttribute(String name, boolean optional){
		return (RGenericVector)getAttribute(name, optional);
	}

	public RIntegerVector getIntegerAttribute(String name){
		return (RIntegerVector)getAttribute(name);
	}

	public RIntegerVector getIntegerAttribute(String name, boolean optional){
		return (RIntegerVector)getAttribute(name, optional);
	}

	public RStringVector getStringAttribute(String name){
		return (RStringVector)getAttribute(name);
	}

	public RStringVector getStringAttribute(String name, boolean optional){
		return (RStringVector)getAttribute(name, optional);
	}

	public boolean hasAttribute(String tag){
		RPair attribute = getAttributes();

		while(attribute != null){

			if(attribute.tagEquals(tag)){
				return true;
			}

			attribute = attribute.getNext();
		}

		return false;
	}

	private RExp findAttribute(String tag, boolean optional){
		RPair attribute = getAttributes();

		while(attribute != null){

			if(attribute.tagEquals(tag)){
				return attribute.getValue();
			}

			attribute = attribute.getNext();
		}

		if(optional){
			return null;
		}

		throw new IllegalArgumentException(tag);
	}

	public RPair getAttributes(){
		return this.attributes;
	}

	private void setAttributes(RPair attributes){
		this.attributes = attributes;
	}
}
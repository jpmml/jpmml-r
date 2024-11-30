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

import java.io.IOException;

abstract
public class RExp {

	private RPair attributes = null;


	public RExp(RPair attributes){
		setAttributes(attributes);
	}

	@SuppressWarnings("unused")
	public void write(RDataOutput output) throws IOException {
		throw new UnsupportedOperationException();
	}

	public RStringVector names(){
		return getStringAttribute("names");
	}

	public RIntegerVector dim(){
		return getIntegerAttribute("dim");
	}

	public RStringVector dimnames(int index){
		RGenericVector dimnames = getGenericAttribute("dimnames");

		return dimnames.getStringValue(index);
	}

	public RExp getAttribute(String name){
		return getAttribute(name, true);
	}

	public RExp getAttribute(String name, boolean required){
		return findAttribute(name, required);
	}

	public RBooleanVector getBooleanAttribute(String name){
		return getBooleanAttribute(name, true);
	}

	public RBooleanVector getBooleanAttribute(String name, boolean required){
		return getVectorAttribute(RBooleanVector.class, name, required);
	}

	public RDoubleVector getDoubleAttribute(String name){
		return getDoubleAttribute(name, true);
	}

	public RDoubleVector getDoubleAttribute(String name, boolean required){
		return getVectorAttribute(RDoubleVector.class, name, required);
	}

	public RGenericVector getGenericAttribute(String name){
		return getGenericAttribute(name, true);
	}

	public RGenericVector getGenericAttribute(String name, boolean required){
		return getVectorAttribute(RGenericVector.class, name, required);
	}

	public RIntegerVector getIntegerAttribute(String name){
		return getIntegerAttribute(name, true);
	}

	public RIntegerVector getIntegerAttribute(String name, boolean required){
		return getVectorAttribute(RIntegerVector.class, name, required);
	}

	public RStringVector getStringAttribute(String name){
		return getStringAttribute(name, true);
	}

	public RStringVector getStringAttribute(String name, boolean required){
		return getVectorAttribute(RStringVector.class, name, required);
	}

	private <V extends RVector<E>, E> V getVectorAttribute(Class<V> clazz, String name, boolean required){
		RExp rexp = getAttribute(name, required);

		try {
			return clazz.cast(rexp);
		} catch(ClassCastException cce){
			throw new IllegalArgumentException("Invalid \'" + name + "\' attribute. Expected " + RExpUtil.getVectorType(clazz) + ", got " + RExpUtil.getVectorType(rexp.getClass()));
		}
	}

	public boolean hasAttribute(String name){
		RPair attribute = getAttributes();

		while(attribute != null){

			if(attribute.tagEquals(name)){
				return true;
			}

			attribute = attribute.getNext();
		}

		return false;
	}

	private RExp findAttribute(String name, boolean required){
		RPair attribute = getAttributes();

		while(attribute != null){

			if(attribute.tagEquals(name)){
				return attribute.getValue();
			}

			attribute = attribute.getNext();
		}

		if(required){
			throw new IllegalArgumentException("Missing \'" + name + "\' attribute");
		}

		return null;
	}

	public RPair getAttributes(){
		return this.attributes;
	}

	void setAttributes(RPair attributes){
		this.attributes = attributes;
	}
}
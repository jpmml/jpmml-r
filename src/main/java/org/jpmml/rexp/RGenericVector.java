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

import org.dmg.pmml.DataType;

public class RGenericVector extends RVector<RExp> {

	private List<RExp> values = null;


	public RGenericVector(List<RExp> values, RPair attributes){
		super(attributes);

		setValues(values);
	}

	@Override
	public DataType getDataType(){
		throw new UnsupportedOperationException();
	}

	@Override
	public int size(){
		return this.values.size();
	}

	@Override
	public RExp getValue(int index){
		return this.values.get(index);
	}

	public RBooleanVector getBooleanElement(String name){
		return (RBooleanVector)getElement(name);
	}

	public RBooleanVector getBooleanElement(String name, boolean optional){
		return (RBooleanVector)getElement(name, optional);
	}

	public RDoubleVector getDoubleElement(String name){
		return (RDoubleVector)getElement(name);
	}

	public RDoubleVector getDoubleElement(String name, boolean optional){
		return (RDoubleVector)getElement(name, optional);
	}

	public RGenericVector getGenericElement(String name){
		return (RGenericVector)getElement(name);
	}

	public RGenericVector getGenericElement(String name, boolean optional){
		return (RGenericVector)getElement(name, optional);
	}

	public RIntegerVector getFactorElement(String name){
		return getIntegerElement(name);
	}

	public RIntegerVector getFactorElement(String name, boolean optional){
		return getIntegerElement(name, optional);
	}

	public RIntegerVector getIntegerElement(String name){
		return (RIntegerVector)getElement(name);
	}

	public RIntegerVector getIntegerElement(String name, boolean optional){
		return (RIntegerVector)getElement(name, optional);
	}

	public RNumberVector<?> getNumericElement(String name){
		return (RNumberVector<?>)getElement(name);
	}

	public RNumberVector<?> getNumericElement(String name, boolean optional){
		return (RNumberVector<?>)getElement(name, optional);
	}

	public RStringVector getStringElement(String name){
		return (RStringVector)getElement(name);
	}

	public RStringVector getStringElement(String name, boolean optional){
		return (RStringVector)getElement(name, optional);
	}

	public RVector<?> getVectorElement(String name){
		return (RVector<?>)getElement(name);
	}

	public RVector<?> getVectorElement(String name, boolean optional){
		return (RVector<?>)getElement(name, optional);
	}

	@Override
	public List<RExp> getValues(){
		return this.values;
	}

	private void setValues(List<RExp> values){
		this.values = values;
	}
}
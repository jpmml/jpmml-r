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

	public RBooleanVector getBooleanValue(String name){
		return (RBooleanVector)getValue(name);
	}

	public RBooleanVector getBooleanValue(String name, boolean optional){
		return (RBooleanVector)getValue(name, optional);
	}

	public RDoubleVector getDoubleValue(String name){
		return (RDoubleVector)getValue(name);
	}

	public RDoubleVector getDoubleValue(String name, boolean optional){
		return (RDoubleVector)getValue(name, optional);
	}

	public RGenericVector getGenericValue(String name){
		return (RGenericVector)getValue(name);
	}

	public RGenericVector getGenericValue(String name, boolean optional){
		return (RGenericVector)getValue(name, optional);
	}

	public RIntegerVector getFactorValue(String name){
		return getIntegerValue(name);
	}

	public RIntegerVector getFactorValue(String name, boolean optional){
		return getIntegerValue(name, optional);
	}

	public RIntegerVector getIntegerValue(String name){
		return (RIntegerVector)getValue(name);
	}

	public RIntegerVector getIntegerValue(String name, boolean optional){
		return (RIntegerVector)getValue(name, optional);
	}

	public RNumberVector<?> getNumericValue(String name){
		return (RNumberVector<?>)getValue(name);
	}

	public RNumberVector<?> getNumericValue(String name, boolean optional){
		return (RNumberVector<?>)getValue(name, optional);
	}

	public RStringVector getStringValue(String name){
		return (RStringVector)getValue(name);
	}

	public RStringVector getStringValue(String name, boolean optional){
		return (RStringVector)getValue(name, optional);
	}

	public RVector<?> getVectorValue(String name){
		return (RVector<?>)getValue(name);
	}

	public RVector<?> getVectorValue(String name, boolean optional){
		return (RVector<?>)getValue(name, optional);
	}

	@Override
	public List<RExp> getValues(){
		return this.values;
	}

	private void setValues(List<RExp> values){
		this.values = values;
	}
}
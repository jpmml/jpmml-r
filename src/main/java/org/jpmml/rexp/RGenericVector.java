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
		return getBooleanElement(name, true);
	}

	public RBooleanVector getBooleanElement(String name, boolean required){
		return getVectorElement(RBooleanVector.class, name, required);
	}

	public RDoubleVector getDoubleElement(String name){
		return getDoubleElement(name, true);
	}

	public RDoubleVector getDoubleElement(String name, boolean required){
		return getVectorElement(RDoubleVector.class, name, required);
	}

	public RGenericVector getGenericElement(String name){
		return getGenericElement(name, true);
	}

	public RGenericVector getGenericElement(String name, boolean required){
		return getVectorElement(RGenericVector.class, name, required);
	}

	public RIntegerVector getFactorElement(String name){
		return getFactorElement(name, true);
	}

	public RIntegerVector getFactorElement(String name, boolean required){
		RIntegerVector factor = getIntegerElement(name, required);

		if(!RExpUtil.isFactor(factor)){
			throw new IllegalArgumentException("Invalid \'" + name + "\' element. Expected factor, got integer");
		}

		return factor;
	}

	public RIntegerVector getIntegerElement(String name){
		return getIntegerElement(name, true);
	}

	public RIntegerVector getIntegerElement(String name, boolean required){
		return getVectorElement(RIntegerVector.class, name, required);
	}

	public RNumberVector<?> getNumericElement(String name){
		return getNumericElement(name, true);
	}

	public RNumberVector<?> getNumericElement(String name, boolean required){
		return getVectorElement(RNumberVector.class, name, required);
	}

	public RStringVector getStringElement(String name){
		return getStringElement(name, true);
	}

	public RStringVector getStringElement(String name, boolean required){
		return getVectorElement(RStringVector.class, name, required);
	}

	public RVector<?> getVectorElement(String name){
		return getVectorElement(name, true);
	}

	public RVector<?> getVectorElement(String name, boolean required){
		return getVectorElement(RVector.class, name, required);
	}

	private <V extends RVector<E>, E> V getVectorElement(Class<V> clazz, String name, boolean required){
		RExp rexp = getElement(name, required);

		try {
			return clazz.cast(rexp);
		} catch(ClassCastException cce){
			throw new IllegalArgumentException("Invalid \'" + name + "\' element. Expected " + RExpUtil.getVectorType(clazz) + ", got " + RExpUtil.getVectorType(rexp.getClass()));
		}
	}

	@Override
	public List<RExp> getValues(){
		return this.values;
	}

	private void setValues(List<RExp> values){
		this.values = values;
	}
}
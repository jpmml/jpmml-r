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
import java.util.List;

import org.dmg.pmml.DataType;

abstract
public class RVector<E> extends RExp implements Iterable<E> {

	public RVector(RPair attributes){
		super(attributes);
	}

	abstract
	public DataType getDataType();

	abstract
	public int size();

	abstract
	public E getValue(int index);

	abstract
	public List<E> getValues();

	@Override
	public Iterator<E> iterator(){
		List<E> values = getValues();

		return values.iterator();
	}

	public boolean isEmpty(){
		return (size() == 0);
	}

	public E asScalar(){
		List<E> values = getValues();
		if(values.size() != 1){
			throw new IllegalStateException();
		}

		return values.get(0);
	}

	public int indexOf(E value){
		List<E> values = getValues();

		return values.indexOf(value);
	}

	public E getElement(String name){
		return getElement(name, true);
	}

	public E getElement(String name, boolean required){
		return findElement(name, required);
	}

	public boolean hasElement(String name){
		RStringVector names = getStringAttribute("names");

		List<String> values = names.getDequotedValues();

		int index = values.indexOf(name);

		return (index > -1);
	}

	private E findElement(String name, boolean required){
		RStringVector names = getStringAttribute("names");

		List<String> values = names.getDequotedValues();

		int index = values.indexOf(name);
		if(index > -1){
			return getValue(index);
		} // End if

		if(required){
			throw new IllegalArgumentException("Missing \'" + name + "\' element");
		}

		return null;
	}

	@Override
	public String toString(){
		return String.valueOf(getValues());
	}
}
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
import java.util.Objects;

import org.dmg.pmml.DataType;

abstract
public class RVector<V> extends RExp {

	public RVector(RPair attributes){
		super(attributes);
	}

	abstract
	public DataType getDataType();

	abstract
	public int size();

	abstract
	public V getValue(int index);

	abstract
	public List<V> getValues();

	public V asScalar(){
		List<V> values = getValues();
		if(values.size() != 1){
			throw new IllegalStateException();
		}

		return values.get(0);
	}

	public int indexOf(V value){
		List<V> values = getValues();

		return values.indexOf(value);
	}

	public boolean hasValue(String name){
		RPair names = getAttribute("names");
		if(names == null){
			throw new IllegalStateException();
		}

		RStringVector vector = (RStringVector)names.getValue();

		return (vector.indexOf(name) > -1);
	}

	public V getValue(String name){
		return getValue(name, false);
	}

	public V getValue(String name, boolean optional){
		RPair names = getAttribute("names");
		if(names == null){
			throw new IllegalStateException();
		}

		RStringVector vector = (RStringVector)names.getValue();

		List<String> values = vector.getDequotedValues();
		for(int i = 0; i < values.size(); i++){
			String value = values.get(i);

			if(Objects.equals(name, value)){
				return getValue(i);
			}
		}

		if(optional){
			return null;
		}

		throw new IllegalArgumentException(name);
	}

	@Override
	public String toString(){
		return String.valueOf(getValues());
	}
}
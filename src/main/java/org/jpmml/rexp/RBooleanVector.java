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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import org.dmg.pmml.DataType;

public class RBooleanVector extends RVector<Boolean> {

	private int[] values = null;


	public RBooleanVector(int[] values, RPair attributes){
		super(attributes);

		this.values = values;
	}

	@Override
	public DataType getDataType(){
		return DataType.BOOLEAN;
	}

	@Override
	public int size(){
		return this.values.length;
	}

	@Override
	public Boolean getValue(int index){
		int value = this.values[index];

		if(value == Integer.MIN_VALUE){
			return null;
		}

		return Boolean.valueOf(value == 1);
	}

	@Override
	public List<Boolean> getValues(){
		Function<Integer, Boolean> function = new Function<Integer, Boolean>(){

			@Override
			public Boolean apply(Integer value){

				if(value == Integer.MIN_VALUE){
					return null;
				}

				return (value == 1);
			}
		};

		return Lists.transform(Ints.asList(this.values), function);
	}
}
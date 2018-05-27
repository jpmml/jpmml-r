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

public class RIntegerVector extends RNumberVector<Integer> {

	private int[] values = null;


	public RIntegerVector(int[] values, RPair attributes){
		super(attributes);

		this.values = values;
	}

	@Override
	public DataType getDataType(){

		if(isFactor()){
			return DataType.STRING;
		}

		return DataType.INTEGER;
	}

	@Override
	public int size(){
		return this.values.length;
	}

	@Override
	public Integer getValue(int index){
		int value = this.values[index];

		if(value == Integer.MIN_VALUE){
			return null;
		}

		return value;
	}

	@Override
	public List<Integer> getValues(){
		Function<Integer, Integer> function = new Function<Integer, Integer>(){

			@Override
			public Integer apply(Integer value){

				if(value == Integer.MIN_VALUE){
					return null;
				}

				return value;
			}
		};

		return Lists.transform(Ints.asList(this.values), function);
	}

	public boolean isFactor(){
		return hasAttribute("levels");
	}

	public RStringVector getLevels(){
		RPair levels = getAttribute("levels");
		if(levels == null){
			throw new IllegalStateException();
		}

		return (RStringVector)levels.getValue();
	}

	public List<String> getLevelValues(){
		RStringVector levels = getLevels();

		return levels.getValues();
	}

	public String getFactorValue(int index){
		RStringVector levels = getLevels();

		Integer value = getValue(index);
		if(value == null){
			return null;
		}

		return levels.getValue(value - 1);
	}

	public List<String> getFactorValues(){
		List<Integer> values = getValues();
		List<String> levelValues = getLevelValues();

		Function<Integer, String> function = new Function<Integer, String>(){

			@Override
			public String apply(Integer value){

				if(value == null){
					return null;
				}

				return levelValues.get(value - 1);
			}
		};

		return Lists.transform(values, function);
	}
}
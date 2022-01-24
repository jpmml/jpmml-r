/*
 * Copyright (c) 2021 Villu Ruusmann
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
import org.dmg.pmml.DataType;

public class RFactorVector extends RIntegerVector {

	public RFactorVector(int[] values, RPair attributes){
		super(values, attributes);
	}

	@Override
	public DataType getDataType(){
		return DataType.STRING;
	}

	public RStringVector getLevels(){
		return getStringAttribute("levels");
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
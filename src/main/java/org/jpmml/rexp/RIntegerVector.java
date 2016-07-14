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

import com.google.common.primitives.Ints;


public class RIntegerVector extends RNumberVector<Integer> {

	private int[] values = null;


	public RIntegerVector(int[] values, RPair attributes){
		super(attributes);

		this.values = values;
	}

	@Override
	public int size(){
		return this.values.length;
	}

	@Override
	public Integer getValue(int index){
		return this.values[index];
	}

	@Override
	public List<Integer> getValues(){
		return Ints.asList(this.values);
	}

	public RStringVector getLevels(){
		return (RStringVector)getAttributeValue("levels");
	}

	public String getLevelValue(int index){
		RStringVector levels = getLevels();

		return levels.getValue(getValue(index) - 1);
	}

	public List<String> getLevelValues(){
		RStringVector levels = getLevels();

		return levels.getValues();
	}
}
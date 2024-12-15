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
import java.util.Collections;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import org.dmg.pmml.DataType;

public class RIntegerVector extends RNumberVector<Integer> {

	private int[] values = null;


	public RIntegerVector(Number value, RPair attributes){
		this(Collections.singletonList(value), attributes);
	}

	public RIntegerVector(List<Number> values, RPair attributes){
		super(attributes);

		this.values = values.stream()
			.mapToInt(value -> {

				if(value == null){
					return Integer.MIN_VALUE;
				}

				return value.intValue();
			})
			.toArray();
	}

	public RIntegerVector(int[] values, RPair attributes){
		super(attributes);

		this.values = values;
	}

	@Override
	int type(){
		return SExpTypes.INTSXP;
	}

	@Override
	void writeValues(RDataOutput output) throws IOException {
		int[] values = this.values;

		int lengthj = values.length;

		output.writeInt(lengthj);

		for(int i = 0; i < lengthj; i++){
			output.writeInt(values[i]);
		}
	}

	@Override
	public DataType getDataType(){
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

	public static final RIntegerVector EMPTY = new RIntegerVector(new int[0], null);
}
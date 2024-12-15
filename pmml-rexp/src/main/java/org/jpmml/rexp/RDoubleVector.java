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

import com.google.common.primitives.Doubles;
import org.dmg.pmml.DataType;

public class RDoubleVector extends RNumberVector<Double> {

	private double[] values = null;


	public RDoubleVector(Number value, RPair attributes){
		this(Collections.singletonList(value), attributes);
	}

	public RDoubleVector(List<Number> values, RPair attributes){
		super(attributes);

		this.values = values.stream()
			.mapToDouble(value -> {

				if(value == null){
					return Double.NaN;
				}

				return value.doubleValue();
			})
			.toArray();
	}

	public RDoubleVector(double[] values, RPair attributes){
		super(attributes);

		this.values = values;
	}

	@Override
	int type(){
		return SExpTypes.REALSXP;
	}

	@Override
	void writeValues(RDataOutput output) throws IOException {
		double[] values = this.values;

		int length = values.length;

		output.writeInt(length);

		for(int i = 0; i < length; i++){
			output.writeDouble(values[i]);
		}
	}

	@Override
	public DataType getDataType(){
		return DataType.DOUBLE;
	}

	@Override
	public int size(){
		return this.values.length;
	}

	@Override
	public Double getValue(int index){
		return this.values[index];
	}

	@Override
	public List<Double> getValues(){
		return Doubles.asList(this.values);
	}

	public static final RDoubleVector EMPTY = new RDoubleVector(new double[0], null);
}
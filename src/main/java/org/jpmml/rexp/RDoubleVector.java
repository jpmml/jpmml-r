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

import com.google.common.primitives.Doubles;
import org.dmg.pmml.DataType;

public class RDoubleVector extends RNumberVector<Double> {

	private double[] values = null;


	public RDoubleVector(double[] values, RPair attributes){
		super(attributes);

		this.values = values;
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
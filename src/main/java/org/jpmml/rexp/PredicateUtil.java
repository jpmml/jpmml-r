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

import org.dmg.pmml.Array;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ValueUtil;

public class PredicateUtil {

	private PredicateUtil(){
	}

	static
	public Predicate createSimpleSetPredicate(Feature feature, List<String> values){

		if(values.size() == 1){
			String value = values.get(0);

			return createSimplePredicate(feature, SimplePredicate.Operator.EQUAL, value);
		}

		SimpleSetPredicate simpleSetPredicate = new SimpleSetPredicate()
			.setField(feature.getName())
			.setBooleanOperator(SimpleSetPredicate.BooleanOperator.IS_IN)
			.setArray(createArray(feature.getDataType(), values));

		return simpleSetPredicate;
	}

	static
	public Predicate createSimplePredicate(Feature feature, SimplePredicate.Operator operator, String value){
		SimplePredicate simplePredicate = new SimplePredicate()
			.setField(feature.getName())
			.setOperator(operator)
			.setValue(value);

		return simplePredicate;
	}

	static
	private Array createArray(DataType dataType, List<String> values){
		String value = ValueUtil.formatArrayValue(values);

		switch(dataType){
			case STRING:
				return new Array(Array.Type.STRING, value);
			case DOUBLE:
			case FLOAT:
				return new Array(Array.Type.REAL, value);
			case INTEGER:
				return new Array(Array.Type.INT, value);
			default:
				throw new IllegalArgumentException();
		}
	}
}
/*
 * Copyright (c) 2015 Villu Ruusmann
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

import org.dmg.pmml.Array;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.VisitorAction;
import org.jpmml.converter.ValueUtil;

public class RandomForestFieldTypeAnalyzer extends FieldTypeAnalyzer {

	@Override
	public VisitorAction visit(SimpleSetPredicate simpleSetPredicate){
		FieldName field = simpleSetPredicate.getField();
		Array array = simpleSetPredicate.getArray();

		Array.Type type = array.getType();
		switch(type){
			case STRING:
				addDataType(field, DataType.STRING);
				break;
			case INT:
				addDataType(field, DataType.INTEGER);
				break;
			case REAL:
				addDataType(field, DataType.DOUBLE);
				break;
			default:
				throw new IllegalArgumentException();
		}

		return super.visit(simpleSetPredicate);
	}

	@Override
	public VisitorAction visit(SimplePredicate simplePredicate){
		FieldName field = simplePredicate.getField();
		SimplePredicate.Operator operator = simplePredicate.getOperator();
		String value = simplePredicate.getValue();

		if((SimplePredicate.Operator.EQUAL).equals(operator) && (("true").equals(value) || ("false").equals(value))){
			addDataType(field, DataType.BOOLEAN);
		} else

		if((SimplePredicate.Operator.LESS_OR_EQUAL).equals(operator) && ("0.5").equals(value)){
			addDataType(field, DataType.BOOLEAN);
		} else

		if((SimplePredicate.Operator.GREATER_THAN).equals(operator) && ("0.5").equals(value)){
			addDataType(field, DataType.BOOLEAN);
		} else

		if((SimplePredicate.Operator.EQUAL).equals(operator)){
			addDataType(field, ValueUtil.getDataType(value));
		} else

		{
			addDataType(field, DataType.DOUBLE);
		}

		return super.visit(simplePredicate);
	}
}
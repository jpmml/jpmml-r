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

import java.util.Objects;

import org.dmg.pmml.Array;
import org.dmg.pmml.SimpleSetPredicate;

public class InternableSimpleSetPredicate extends SimpleSetPredicate {

	@Override
	public int hashCode(){
		int result = 0;

		result = (31 * result) + Objects.hashCode(this.getField());
		result = (31 * result) + Objects.hashCode(this.getBooleanOperator());
		result = (31 * result) + Objects.hashCode(this.getArrayType());
		result = (31 * result) + Objects.hashCode(this.getArrayValue());

		return result;
	}

	@Override
	public boolean equals(Object object){

		if(object instanceof InternableSimpleSetPredicate){
			InternableSimpleSetPredicate that = (InternableSimpleSetPredicate)object;

			return Objects.equals(this.getField(), that.getField()) && Objects.equals(this.getBooleanOperator(), that.getBooleanOperator()) && Objects.equals(this.getArrayType(), that.getArrayType()) && Objects.equals(this.getArrayValue(), that.getArrayValue());
		}

		return false;
	}

	private Array.Type getArrayType(){
		Array array = getArray();

		return array.getType();
	}

	private String getArrayValue(){
		Array array = getArray();

		return array.getValue();
	}
}
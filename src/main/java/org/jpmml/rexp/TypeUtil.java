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

import org.dmg.pmml.DataType;

public class TypeUtil {

	private TypeUtil(){
	}

	static
	public DataType getDataType(List<String> values){

		if(values.isEmpty()){
			throw new IllegalArgumentException();
		}

		DataType dataType = DataType.INTEGER;

		for(String value : values){

			switch(dataType){
				case INTEGER:
					try {
						Integer.parseInt(value);

						continue;
					} catch(NumberFormatException nfe){
						dataType = DataType.DOUBLE;
					}
					// Falls through
				case DOUBLE:
					try {
						Double.parseDouble(value);

						continue;
					} catch(NumberFormatException nfe){
						dataType = DataType.STRING;
					}
					// Falls through
				case STRING:
					return dataType;
				default:
					throw new IllegalArgumentException();
			}
		}

		return dataType;
	}
}
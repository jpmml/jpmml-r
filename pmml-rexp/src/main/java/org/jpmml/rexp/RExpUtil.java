/*
 * Copyright (c) 2014 Villu Ruusmann
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

import java.util.LinkedHashMap;
import java.util.Map;

import org.dmg.pmml.DataType;

public class RExpUtil {

	private RExpUtil(){
	}

	static
	public DataType getDataType(String type){

		switch(type){
			case "character":
			case "factor":
			case "ordered":
				return DataType.STRING;
			case "numeric":
				return DataType.DOUBLE;
			case "logical":
				return DataType.BOOLEAN;
			default:
				break;
		}

		throw new IllegalArgumentException(type);
	}

	static
	public String getVectorType(Class<?> clazz){
		String vectorType = RExpUtil.vectorTypes.get(clazz);

		if(vectorType == null){

			if(RVector.class.isAssignableFrom(clazz)){
				return "vector";
			} else

			{
				return "non-vector";
			}
		}

		return vectorType;
	}

	static
	public String makeName(String string){
		StringBuilder sb = new StringBuilder();

		if(string.length() == 0){
			sb.append('X');
		} else

		{
			char c = string.charAt(0);

			// "A name starts with a letter or the dot (not followed by a number)"
			if(!(Character.isLetter(c) || (c == '.'))){
				sb.append('X');
			}
		}

		for(int i = 0; i < string.length(); i++){
			char c = string.charAt(i);

			// "A name consists of letters, numbers and the dot or underline character"
			if(!(Character.isLetter(c) || (c >= '0' && c <= '9') || (c == '.' || c == '_'))){
				c = '.';
			}

			sb.append(c);
		}

		return sb.toString();
	}

	private static final Map<Class<?>, String> vectorTypes = new LinkedHashMap<>();

	static {
		vectorTypes.put(RBooleanVector.class, "logical");
		vectorTypes.put(RDoubleVector.class, "numeric");
		vectorTypes.put(RFactorVector.class, "factor");
		vectorTypes.put(RIntegerVector.class, "integer");
		vectorTypes.put(RNumberVector.class, "numeric");
		vectorTypes.put(RStringVector.class, "character");
	}
}
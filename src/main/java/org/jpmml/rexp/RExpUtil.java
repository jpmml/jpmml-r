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

import java.util.List;

import org.dmg.pmml.DataType;

public class RExpUtil {

	private RExpUtil(){
	}

	static
	public DataType getDataType(String type){

		switch(type){
			case "character":
			case "factor":
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
	public List<String> getFactorLevels(RNumberVector<?> factor){
		return getFactorLevels((RIntegerVector)factor);
	}

	static
	public List<String> getFactorLevels(RIntegerVector factor){

		if(!factor.isFactor()){
			throw new IllegalArgumentException();
		}

		return factor.getLevelValues();
	}
}
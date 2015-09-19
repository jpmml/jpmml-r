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

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.math.DoubleMath;
import org.dmg.pmml.DataType;

public class RExpUtil {

	private RExpUtil(){
	}

	static
	public <E> List<E> getRow(List<E> matrix, int k, int rows, int columns){
		List<E> row = new ArrayList<>();

		for(int i = 0; i < columns; i++){
			row.add(matrix.get((i * rows) + k));
		}

		return row;
	}

	static
	public <E> List<E> getColumn(List<E> matrix, int k, int rows, int columns){
		return matrix.subList(k * rows, (k * rows) + rows);
	}

	static
	public boolean inherits(RExp rexp, String name){
		RExp clazz = RExpUtil.attribute(rexp, "class");

		for(int i = 0; i < clazz.getStringValueCount(); i++){
			RString clazzValue = clazz.getStringValue(i);

			if((name).equals(clazzValue.getStrval())){
				return true;
			}
		}

		return false;
	}

	static
	public RExp field(RExp rexp, String name){
		RExp names = attribute(rexp, "names");

		for(int i = 0; i < names.getStringValueCount(); i++){
			RString nameValue = names.getStringValue(i);

			if((name).equals(nameValue.getStrval())){
				return rexp.getRexpValue(i);
			}
		}

		throw new IllegalArgumentException("Field " + name + " not in " + getStringList(names));
	}

	static
	public RBoolean booleanField(RExp rexp, String name){
		RExp names = attribute(rexp, "names");

		for(int i = 0; i < names.getStringValueCount(); i++){
			RString nameValue = names.getStringValue(i);

			if((name).equals(nameValue.getStrval())){
				return rexp.getBooleanValue(i);
			}
		}

		throw new IllegalArgumentException("Field " + name + " not in " + getStringList(names));
	}

	static
	public RExp attribute(RExp rexp, String name){

		for(int i = 0; i < rexp.getAttrNameCount(); i++){

			if((rexp.getAttrName(i)).equals(name)){
				return rexp.getAttrValue(i);
			}
		}

		throw new IllegalArgumentException("Attribute " + name + " not in " + rexp.getAttrNameList());
	}

	static
	public List<String> getStringList(RExp rexp){
		Function<RString, String> function = new Function<RString, String>(){

			@Override
			public String apply(RString string){
				return string.getStrval();
			}
		};

		return Lists.transform(rexp.getStringValueList(), function);
	}

	static
	public DataType getDataType(String type){

		if("factor".equals(type)){
			return DataType.STRING;
		} else

		if("numeric".equals(type)){
			return DataType.DOUBLE;
		} else

		if("logical".equals(type)){
			return DataType.BOOLEAN;
		}

		throw new IllegalArgumentException();
	}

	static
	public Integer asInteger(Number number){

		if(number instanceof Integer){
			return (Integer)number;
		}

		double value = number.doubleValue();

		if(DoubleMath.isMathematicalInteger(value)){
			return number.intValue();
		}

		throw new IllegalArgumentException();
	}

	static
	public Double asDouble(Number number){

		if(number instanceof Double){
			return (Double)number;
		}

		return number.doubleValue();
	}
}
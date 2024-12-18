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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.dmg.pmml.DataType;

public class RStringVector extends RVector<String> {

	private List<String> values = null;


	public RStringVector(String value, RPair attributes){
		this(Collections.singletonList(value), attributes);
	}

	public RStringVector(List<String> values, RPair attributes){
		super(attributes);

		setValues(values);
	}

	@Override
	int type(){
		return SExpTypes.STRSXP;
	}

	@Override
	void writeValues(RDataOutput output) throws IOException {
		List<String> values = getValues();

		int length = values.size();

		output.writeInt(length);

		for(int i = 0; i < length; i++){
			RString string = new RString(values.get(i));

			string.write(output);
		}
	}

	@Override
	public DataType getDataType(){
		return DataType.STRING;
	}

	public RFactorVector toFactorVector(){
		List<String> values = getValues();

		List<String> levels = values.stream()
			.distinct()
			.sorted(Comparator.nullsLast(Comparator.naturalOrder()))
			.collect(Collectors.toList());

		return toFactorVector(levels);
	}

	public RFactorVector toFactorVector(List<String> levels){
		List<String> values = getValues();

		int[] levelValues = values.stream()
			.mapToInt(value -> {
				int index = levels.indexOf(value);

				if(index < 0){
					return Integer.MIN_VALUE;
				}

				return (index + 1);
			})
			.toArray();

		RFactorVector result = new RFactorVector(levelValues, null);
		result.addAttribute("class", new RStringVector(Arrays.asList("factor"), null));
		result.addAttribute("levels", new RStringVector(levels, null));

		return result;
	}

	@Override
	public int size(){
		return this.values.size();
	}

	public String getDequotedValue(int i){
		return RStringVector.FUNCTION_DEQUOTE.apply(getValue(i));
	}

	@Override
	public String getValue(int index){
		return this.values.get(index);
	}

	public List<String> getDequotedValues(){
		return Lists.transform(getValues(), RStringVector.FUNCTION_DEQUOTE);
	}

	@Override
	public List<String> getValues(){
		return this.values;
	}

	private void setValues(List<String> values){
		this.values = values;
	}

	public static final RStringVector EMPTY = new RStringVector(Collections.emptyList(), null);

	private static final Function<String, String> FUNCTION_DEQUOTE = new Function<String, String>(){

		@Override
		public String apply(String string){

			if((string.length() > 1) && (string.charAt(0) == '`' && string.charAt(string.length() - 1) == '`')){
				string = string.substring(1, string.length() - 1);
			}

			return string;
		}
	};
}
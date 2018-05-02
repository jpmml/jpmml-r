/*
 * Copyright (c) 2018 Villu Ruusmann
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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.dmg.pmml.FieldName;

public class CategoryManager extends ValueManager<Set<String>> {

	public CategoryManager(){
	}

	public CategoryManager(Map<FieldName, Set<String>> valueMap){
		super(valueMap);
	}

	public CategoryManager fork(FieldName name, Collection<String> values){
		return fork(name, new LinkedHashSet<>(values));
	}

	@Override
	public CategoryManager fork(FieldName name, Set<String> values){
		Map<FieldName, Set<String>> valueMap = new LinkedHashMap<>(getValueMap());

		valueMap.put(name, values);

		return new CategoryManager(valueMap);
	}

	public Predicate<String> getValueFilter(FieldName name){
		Set<String> values = getValue(name);

		Predicate<String> predicate = new Predicate<String>(){

			@Override
			public boolean test(String value){

				if(values != null){
					return values.contains(value);
				}

				return true;
			}
		};

		return predicate;
	}
}
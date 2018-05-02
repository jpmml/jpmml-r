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

import java.util.Collections;
import java.util.Map;

import org.dmg.pmml.FieldName;

abstract
public class ValueManager<V> {

	private Map<FieldName, V> valueMap = Collections.emptyMap();


	public ValueManager(){
	}

	public ValueManager(Map<FieldName, V> valueMap){
		setValueMap(valueMap);
	}

	abstract
	public ValueManager<V> fork(FieldName name, V value);

	public V getValue(FieldName name){
		Map<FieldName, V> valueMap = getValueMap();

		return valueMap.get(name);
	}

	public Map<FieldName, V> getValueMap(){
		return this.valueMap;
	}

	private void setValueMap(Map<FieldName, V> valueMap){

		if(valueMap == null){
			throw new IllegalArgumentException();
		}

		this.valueMap = valueMap;
	}
}
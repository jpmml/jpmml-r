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

import java.util.HashMap;
import java.util.Map;

import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.jpmml.model.visitors.AbstractVisitor;

abstract
public class FieldTypeAnalyzer extends AbstractVisitor {

	private Map<FieldName, DataType> fieldDataTypes = new HashMap<>();


	public void addDataType(FieldName name, DataType dataType){
		DataType fieldDataType = this.fieldDataTypes.get(name);
		if(fieldDataType == null){
			this.fieldDataTypes.put(name, dataType);

			return;
		}

		switch(fieldDataType){
			case STRING:
				return;
			case DOUBLE:
				switch(dataType){
					case STRING:
						this.fieldDataTypes.put(name, dataType);
						return;
					case DOUBLE:
					case FLOAT:
					case INTEGER:
					case BOOLEAN:
						return;
					default:
						throw new IllegalArgumentException();
				}
			case FLOAT:
				switch(dataType){
					case STRING:
					case DOUBLE:
						this.fieldDataTypes.put(name, dataType);
						return;
					case FLOAT:
					case INTEGER:
					case BOOLEAN:
						return;
					default:
						throw new IllegalArgumentException();
				}
			case INTEGER:
				switch(dataType){
					case STRING:
					case DOUBLE:
					case FLOAT:
						this.fieldDataTypes.put(name, dataType);
						return;
					case INTEGER:
					case BOOLEAN:
						return;
					default:
						throw new IllegalArgumentException();
				}
			case BOOLEAN:
				switch(dataType){
					case STRING:
					case DOUBLE:
					case FLOAT:
					case INTEGER:
						this.fieldDataTypes.put(name, dataType);
						return;
					case BOOLEAN:
						return;
					default:
						throw new IllegalArgumentException();
				}
			default:
				throw new IllegalArgumentException();
		}
	}

	public DataType getDataType(FieldName name){
		return this.fieldDataTypes.get(name);
	}
}
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

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.dmg.pmml.Array;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.OpType;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Value;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.ValueUtil;

public class DataFieldUtil {

	private DataFieldUtil(){
	}

	static
	public DataField createDataField(FieldName name, boolean categorical){
		return createDataField(name, categorical ? DataType.STRING : DataType.DOUBLE);
	}

	static
	public DataField createDataField(FieldName name, DataType dataType){
		DataField dataField = new DataField()
			.setName(name);

		dataField = refineDataField(dataField, dataType);

		return dataField;
	}

	static
	public DataField refineDataField(DataField dataField){
		Function<Value, String> function = new Function<Value, String>(){

			@Override
			public String apply(Value value){
				return value.getValue();
			}
		};

		DataType dataType = ValueUtil.getDataType(Lists.transform(dataField.getValues(), function));

		return refineDataField(dataField, dataType);
	}

	static
	public DataField refineDataField(DataField dataField, DataType dataType){
		List<Value> values = dataField.getValues();

		switch(dataType){
			case STRING:
				return dataField.setDataType(DataType.STRING)
					.setOpType(OpType.CATEGORICAL);
			case DOUBLE:
			case FLOAT:
				return dataField.setDataType(dataType)
					.setOpType(values.size() > 0 ? OpType.CATEGORICAL : OpType.CONTINUOUS);
			case INTEGER:
				return dataField.setDataType(DataType.INTEGER)
					.setOpType(values.size() > 0 ? OpType.CATEGORICAL : OpType.CONTINUOUS);
			case BOOLEAN:
				return dataField.setDataType(DataType.BOOLEAN)
					.setOpType(OpType.CATEGORICAL);
			default:
				throw new IllegalArgumentException();
		}
	}

	static
	public List<DataField> refineDataFields(List<DataField> dataFields, FieldTypeAnalyzer fieldTypeAnalyzer){

		for(DataField dataField : dataFields){
			DataType dataType = fieldTypeAnalyzer.getDataType(dataField.getName());

			if(dataType == null){
				continue;
			}

			dataField = refineDataField(dataField, dataType);
		}

		return dataFields;
	}

	static
	public Array createArray(DataField dataField, List<Value> values){
		Function<Value, String> function = new Function<Value, String>(){

			@Override
			public String apply(Value value){
				return ValueUtil.formatValue(value.getValue());
			}
		};

		String value = ValueUtil.formatArrayValue(Lists.transform(values, function));

		DataType dataType = dataField.getDataType();
		switch(dataType){
			case STRING:
				return new Array(Array.Type.STRING, value);
			case DOUBLE:
			case FLOAT:
				return new Array(Array.Type.REAL, value);
			case INTEGER:
				return new Array(Array.Type.INT, value);
			default:
				throw new IllegalArgumentException();
		}
	}

	static
	public MiningSchema createMiningSchema(List<DataField> dataFields){
		return createMiningSchema(dataFields, null);
	}

	static
	public MiningSchema createMiningSchema(List<DataField> dataFields, PMMLObject object){
		return createMiningSchema(dataFields.get(0), dataFields.subList(1, dataFields.size()), object);
	}

	static
	public MiningSchema createMiningSchema(DataField targetDataField, List<DataField> activeDataFields, PMMLObject object){
		Function<DataField, FieldName> function = new Function<DataField, FieldName>(){

			@Override
			public FieldName apply(DataField dataField){

				if(dataField == null){
					return null;
				}

				return dataField.getName();
			}
		};

		return ModelUtil.createMiningSchema(function.apply(targetDataField), Lists.transform(activeDataFields, function), object);
	}

	static
	public List<OutputField> createProbabilityFields(DataField dataField){
		List<Value> values = dataField.getValues();

		Function<Value, OutputField> function = new Function<Value, OutputField>(){

			@Override
			public OutputField apply(Value value){
				return ModelUtil.createProbabilityField(value.getValue());
			}
		};

		return new ArrayList<>(Lists.transform(values, function));
	}
}
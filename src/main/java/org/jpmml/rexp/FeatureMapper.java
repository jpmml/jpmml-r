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
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Value;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ListFeature;
import org.jpmml.converter.PMMLMapper;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;

public class FeatureMapper extends PMMLMapper {

	public void append(FieldName name, boolean categorical){
		append(name, categorical ? DataType.STRING : DataType.DOUBLE);
	}

	public void append(FieldName name, DataType dataType){
		OpType opType;

		switch(dataType){
			case STRING:
			case BOOLEAN:
				opType = OpType.CATEGORICAL;
				break;
			case INTEGER:
			case FLOAT:
			case DOUBLE:
				opType = OpType.CONTINUOUS;
				break;
			default:
				throw new IllegalArgumentException();
		}

		DataField dataField = createDataField(name, opType, dataType);
	}

	public void append(FieldName name, List<String> categories){
		append(name, ValueUtil.getDataType(categories), categories);
	}

	public void append(FieldName name, DataType dataType, List<String> categories){
		DataField dataField = createDataField(name, OpType.CATEGORICAL, dataType);

		if(categories != null && categories.size() > 0){
			List<Value> values = dataField.getValues();

			values.addAll(PMMLUtil.createValues(categories));
		}
	}

	public void append(DataField dataField){
		addDataField(dataField);
	}

	public Schema createSupervisedSchema(){
		List<FieldName> names = names();

		return createSchema(names.get(0), names.subList(1, names.size()));
	}

	public Schema createUnsupervisedSchema(){
		List<FieldName> names = names();

		return createSchema(null, names);
	}

	public Schema createSchema(FieldName targetField, List<FieldName> activeFields){
		DataField targetDataField = getDataField(targetField);

		List<String> targetCategories = null;

		if(targetDataField != null && targetDataField.hasValues()){
			targetCategories = getValues(targetDataField);
		}

		List<Feature> features = new ArrayList<>();

		for(FieldName activeField : activeFields){
			DataField activeDataField = getDataField(activeField);

			Feature feature;

			if(activeDataField.hasValues()){
				List<String> categories = getValues(activeDataField);

				feature = new ListFeature(activeDataField, categories);
			} else

			{
				feature = new ContinuousFeature(activeDataField);
			}

			features.add(feature);
		}

		Schema schema = new Schema(targetField, targetCategories, activeFields, features);

		return schema;
	}

	private List<FieldName> names(){
		Map<FieldName, DataField> dataFields = getDataFields();

		return new ArrayList<>(dataFields.keySet());
	}

	static
	private List<String> getValues(DataField dataField){
		Function<Value, String> function = new Function<Value, String>(){

			@Override
			public String apply(Value value){
				Value.Property property = value.getProperty();

				switch(property){
					case VALID:
						return value.getValue();
					default:
						throw new IllegalArgumentException();
				}
			}
		};

		List<Value> values = dataField.getValues();

		return new ArrayList<>(Lists.transform(values, function));
	}
}
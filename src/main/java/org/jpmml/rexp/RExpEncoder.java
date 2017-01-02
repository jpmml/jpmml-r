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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMML;
import org.dmg.pmml.TypeDefinitionField;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.Label;
import org.jpmml.converter.ModelEncoder;
import org.jpmml.converter.Schema;
import org.jpmml.model.visitors.FieldRenamer;

public class RExpEncoder extends ModelEncoder {

	private Map<FieldName, Feature> features = new LinkedHashMap<>();

	private Map<FieldName, FieldName> renamedFields = new LinkedHashMap<>();


	@Override
	public PMML encodePMML(Model model){
		PMML pmml = super.encodePMML(model);

		Collection<Map.Entry<FieldName, FieldName>> entries = this.renamedFields.entrySet();
		for(Map.Entry<FieldName, FieldName> entry : entries){
			FieldRenamer renamer = new FieldRenamer(entry.getKey(), entry.getValue());

			renamer.applyTo(pmml);
		}

		return pmml;
	}

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

		this.features.put(dataField.getName(), null);
	}

	public void append(FieldName name, List<String> categories){
		append(name, TypeUtil.getDataType(categories), categories);
	}

	public void append(FieldName name, DataType dataType, List<String> categories){
		DataField dataField = createDataField(name, OpType.CATEGORICAL, dataType, categories);

		this.features.put(dataField.getName(), null);
	}

	public void append(DataField dataField){
		addDataField(dataField);

		this.features.put(dataField.getName(), null);
	}

	public void append(Feature feature){
		append(feature.getName(), feature);
	}

	public void append(FieldName name, Feature feature){
		this.features.put(name, feature);
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
		Label label = null;

		if(targetField != null){
			DataField dataField = getDataField(targetField);

			if(dataField.hasValues()){
				label = new CategoricalLabel(dataField);
			} else

			{
				label = new ContinuousLabel(dataField);
			}
		}

		List<Feature> features = new ArrayList<>();

		for(FieldName activeField : activeFields){
			Feature feature = getFeature(activeField);

			if(feature == null){
				TypeDefinitionField field = getField(activeField);

				if(field.hasValues()){
					feature = new CategoricalFeature(this, (DataField)field);
				} else

				{
					feature = new ContinuousFeature(this, field);
				}
			}

			features.add(feature);
		}

		Schema schema = new Schema(label, features);

		return schema;
	}

	public DataField getTargetField(){
		List<FieldName> names = names();

		if(names.size() < 1){
			throw new IllegalArgumentException();
		}

		return getDataField(names.get(0));
	}

	private List<FieldName> names(){
		Map<FieldName, Feature> features = getFeatures();

		return new ArrayList<>(features.keySet());
	}

	public void renameField(FieldName from, FieldName to){
		this.renamedFields.put(from, to);
	}

	private Feature getFeature(FieldName name){
		return this.features.get(name);
	}

	private Map<FieldName, Feature> getFeatures(){
		return this.features;
	}
}
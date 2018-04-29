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
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMML;
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

	private Label label = null;

	private List<Feature> features = new ArrayList<>();

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

	public void addFields(RExpEncoder encoder){
		Map<FieldName, DataField> dataFields = encoder.getDataFields();
		Map<FieldName, DerivedField> derivedFields = encoder.getDerivedFields();

		for(FieldName name : dataFields.keySet()){
			DataField dataField = getDataField(name);

			if(dataField == null){
				dataField = dataFields.get(name);

				addDataField(dataField);
			}
		}

		for(FieldName name : derivedFields.keySet()){
			DerivedField derivedField = getDerivedField(name);

			if(derivedField == null){
				derivedField = derivedFields.get(name);

				addDerivedField(derivedField);
			}
		}
	}

	@Override
	public DataField createDataField(FieldName name, OpType opType, DataType dataType, List<String> values){

		if(dataType == null){
			dataType = TypeUtil.getDataType(values);
		}

		return super.createDataField(name, opType, dataType, values);
	}

	public Schema createSchema(){
		Schema schema = new Schema(getLabel(), getFeatures());

		return schema;
	}

	public void setLabel(DataField dataField){
		Label label;

		OpType opType = dataField.getOpType();
		switch(opType){
			case CATEGORICAL:
				label = new CategoricalLabel(dataField);
				break;
			case CONTINUOUS:
				label = new ContinuousLabel(dataField);
				break;
			default:
				throw new IllegalArgumentException();
		}

		setLabel(label);
	}

	public void addFeature(Field<?> field){
		Feature feature;

		OpType opType = field.getOpType();
		switch(opType){
			case CATEGORICAL:
				feature = new CategoricalFeature(this, (DataField)field);
				break;
			case CONTINUOUS:
				feature = new ContinuousFeature(this, field);
				break;
			default:
				throw new IllegalArgumentException();
		}

		addFeature(feature);
	}

	public void addFeature(Feature feature){
		List<Feature> features = getFeatures();

		features.add(feature);
	}

	public Label getLabel(){
		return this.label;
	}

	public void setLabel(Label label){
		this.label = label;
	}

	public List<Feature> getFeatures(){
		return this.features;
	}

	public void renameField(FieldName name, FieldName renamedName){
		this.renamedFields.put(name, renamedName);
	}
}
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.TypeDefinitionField;
import org.jpmml.converter.BinaryFeature;
import org.jpmml.converter.BooleanFeature;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;

public class Formula {

	private RExpEncoder encoder = null;

	private List<TypeDefinitionField> fields = new ArrayList<>();

	private Map<FieldName, CategoricalFeature> categoricalFeatures = new LinkedHashMap<>();

	private BiMap<FieldName, BinaryFeature> binaryFeatures = HashBiMap.create();


	public Formula(RExpEncoder encoder){
		setEncoder(encoder);
	}

	public Feature resolveFeature(FieldName name){
		RExpEncoder encoder = getEncoder();

		CategoricalFeature categoricalFeature = this.categoricalFeatures.get(name);
		if(categoricalFeature != null){
			return categoricalFeature;
		}

		BinaryFeature binaryFeature = this.binaryFeatures.get(name);
		if(binaryFeature != null){
			return binaryFeature;
		}

		TypeDefinitionField field = encoder.getField(name);

		Feature feature = new ContinuousFeature(encoder, field);

		return feature;
	}

	public Double getCoefficient(Feature feature, RDoubleVector coefficients){
		FieldName name = feature.getName();

		if(feature instanceof BinaryFeature){
			BinaryFeature binaryFeature = (BinaryFeature)feature;

			BiMap<BinaryFeature, FieldName> inverseBinaryFeatures = this.binaryFeatures.inverse();

			name = inverseBinaryFeatures.get(binaryFeature);
		}

		return coefficients.getValue(name.getValue());
	}

	public TypeDefinitionField getField(int index){
		return this.fields.get(index);
	}

	public void addField(TypeDefinitionField field){
		this.fields.add(field);
	}

	public void addField(TypeDefinitionField field, List<String> categories){
		addField(field, categories, categories);
	}

	public void addField(TypeDefinitionField field, List<String> categoryNames, List<String> categoryValues){
		RExpEncoder encoder = getEncoder();

		if(categoryNames.size() != categoryValues.size()){
			throw new IllegalArgumentException();
		}

		FieldName name = field.getName();

		CategoricalFeature categoricalFeature;

		if((DataType.BOOLEAN).equals(field.getDataType()) && (categoryValues.size() == 2) && ("false").equals(categoryValues.get(0)) && ("true").equals(categoryValues.get(1))){
			categoricalFeature = new BooleanFeature(encoder, field);
		} else

		{
			categoricalFeature = new CategoricalFeature(encoder, field, categoryValues);
		}

		this.categoricalFeatures.put(name, categoricalFeature);

		for(int i = 0; i < categoryNames.size(); i++){
			String categoryName = categoryNames.get(i);
			String categoryValue = categoryValues.get(i);

			BinaryFeature binaryFeature = new BinaryFeature(encoder, field, categoryValue);

			this.binaryFeatures.put(FieldName.create(name.getValue() + categoryName), binaryFeature);
		}

		this.fields.add(field);
	}

	public RExpEncoder getEncoder(){
		return this.encoder;
	}

	private void setEncoder(RExpEncoder encoder){
		this.encoder = encoder;
	}
}
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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.dmg.pmml.Apply;
import org.dmg.pmml.Constant;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.TypeDefinitionField;
import org.jpmml.converter.BinaryFeature;
import org.jpmml.converter.BooleanFeature;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.HasDerivedName;
import org.jpmml.converter.PowerFeature;

public class Formula {

	private RExpEncoder encoder = null;

	private BiMap<FieldName, Feature> features = HashBiMap.create();

	private List<TypeDefinitionField> fields = new ArrayList<>();


	public Formula(RExpEncoder encoder){
		setEncoder(encoder);
	}

	public Feature resolveFeature(FieldName name){
		Feature feature = this.features.get(name);

		if(feature == null){
			throw new IllegalArgumentException();
		}

		return feature;
	}

	public Double getCoefficient(Feature feature, RDoubleVector coefficients){
		FieldName name = feature.getName();

		if(feature instanceof HasDerivedName){
			BiMap<Feature, FieldName> inverseFeatures = this.features.inverse();

			name = inverseFeatures.get(feature);
		}

		return coefficients.getValue(name.getValue());
	}

	public TypeDefinitionField getField(int index){
		return this.fields.get(index);
	}

	public void addField(TypeDefinitionField field){
		RExpEncoder encoder = getEncoder();

		Feature feature = new ContinuousFeature(encoder, field);

		if(field instanceof DerivedField){
			DerivedField derivedField = (DerivedField)field;

			Expression expression = derivedField.getExpression();
			if(expression instanceof Apply){
				Apply apply = (Apply)expression;

				if(checkApply(apply, "pow", FieldRef.class, Constant.class)){
					List<Expression> expressions = apply.getExpressions();

					FieldRef fieldRef = (FieldRef)expressions.get(0);
					Constant constant = (Constant)expressions.get(1);

					try {
						int power = Integer.parseInt(constant.getValue());

						feature = new PowerFeature(encoder, fieldRef.getField(), DataType.DOUBLE, power);
					} catch(NumberFormatException nfe){
						// Ignored
					}
				}
			}
		}

		this.features.put(field.getName(), feature);

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

		CategoricalFeature categoricalFeature;

		if((DataType.BOOLEAN).equals(field.getDataType()) && (categoryValues.size() == 2) && ("false").equals(categoryValues.get(0)) && ("true").equals(categoryValues.get(1))){
			categoricalFeature = new BooleanFeature(encoder, field);
		} else

		{
			categoricalFeature = new CategoricalFeature(encoder, field, categoryValues);
		}

		this.features.put(field.getName(), categoricalFeature);

		for(int i = 0; i < categoryNames.size(); i++){
			String categoryName = categoryNames.get(i);
			String categoryValue = categoryValues.get(i);

			BinaryFeature binaryFeature = new BinaryFeature(encoder, field, categoryValue);

			this.features.put(FieldName.create((field.getName()).getValue() + categoryName), binaryFeature);
		}

		this.fields.add(field);
	}

	public RExpEncoder getEncoder(){
		return this.encoder;
	}

	private void setEncoder(RExpEncoder encoder){
		this.encoder = encoder;
	}

	static
	private boolean checkApply(Apply apply, String function, Class<? extends Expression>... expressionClazzes){

		if((function).equals(apply.getFunction())){
			List<Expression> expressions = apply.getExpressions();

			if(expressionClazzes.length == expressions.size()){

				for(int i = 0; i < expressionClazzes.length; i++){
					Class<? extends Expression> expressionClazz = expressionClazzes[i];
					Expression expression = expressions.get(i);

					if(!(expressionClazz).isInstance(expression)){
						return false;
					}
				}

				return true;
			}
		}

		return false;
	}
}
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.dmg.pmml.Apply;
import org.dmg.pmml.Constant;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.PMMLFunctions;
import org.jpmml.converter.BinaryFeature;
import org.jpmml.converter.BooleanFeature;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.HasDerivedName;
import org.jpmml.converter.InteractionFeature;
import org.jpmml.converter.PowerFeature;
import org.jpmml.model.ValueUtil;

public class Formula {

	private RExpEncoder encoder = null;

	private Map<FieldName, FieldName> validNames = new HashMap<>();

	private BiMap<FieldName, Feature> features = HashBiMap.create();

	private List<Field<?>> fields = new ArrayList<>();


	public Formula(RExpEncoder encoder){
		setEncoder(encoder);
	}

	public Feature resolveFeature(String name){
		RExpEncoder encoder = getEncoder();

		List<String> variables = split(name);
		if(variables.size() == 1){
			return resolveFeature(FieldName.create(name));
		} else

		{
			List<Feature> variableFeatures = new ArrayList<>();

			for(String variable : variables){
				Feature variableFeature = resolveFeature(FieldName.create(variable));

				variableFeatures.add(variableFeature);
			}

			return new InteractionFeature(encoder, FieldName.create(name), DataType.DOUBLE, variableFeatures);
		}
	}

	public Feature resolveFeature(FieldName name){
		Feature feature = getFeature(name);

		if(feature == null){
			throw new IllegalArgumentException(name.getValue());
		}

		return feature;
	}

	public Double getCoefficient(Feature feature, RDoubleVector coefficients){
		FieldName name = feature.getName();

		if(feature instanceof HasDerivedName){
			BiMap<Feature, FieldName> inverseFeatures = this.features.inverse();

			name = inverseFeatures.get(feature);
		}

		return coefficients.getElement(name.getValue());
	}

	public Field<?> getField(int index){
		return this.fields.get(index);
	}

	public void addField(Field<?> field){
		RExpEncoder encoder = getEncoder();

		Feature feature = new ContinuousFeature(encoder, field);

		if(field instanceof DerivedField){
			DerivedField derivedField = (DerivedField)field;

			Expression expression = derivedField.getExpression();
			if(expression instanceof Apply){
				Apply apply = (Apply)expression;

				if(checkApply(apply, PMMLFunctions.POW, FieldRef.class, Constant.class)){
					List<Expression> expressions = apply.getExpressions();

					FieldRef fieldRef = (FieldRef)expressions.get(0);
					Constant constant = (Constant)expressions.get(1);

					try {
						String string = ValueUtil.toString(constant.getValue());

						int power = Integer.parseInt(string);

						feature = new PowerFeature(encoder, fieldRef.getField(), DataType.DOUBLE, power);
					} catch(NumberFormatException nfe){
						// Ignored
					}
				}
			}
		}

		putFeature(field.getName(), feature);

		this.fields.add(field);
	}

	public void addField(Field<?> field, List<String> categoryNames, List<?> categoryValues){
		RExpEncoder encoder = getEncoder();

		if(categoryNames.size() != categoryValues.size()){
			throw new IllegalArgumentException();
		}

		CategoricalFeature categoricalFeature;

		if((DataType.BOOLEAN).equals(field.getDataType()) && (BooleanFeature.VALUES).equals(categoryValues)){
			categoricalFeature = new BooleanFeature(encoder, field);
		} else

		{
			categoricalFeature = new CategoricalFeature(encoder, field, categoryValues);
		}

		putFeature(field.getName(), categoricalFeature);

		for(int i = 0; i < categoryNames.size(); i++){
			String categoryName = categoryNames.get(i);
			Object categoryValue = categoryValues.get(i);

			BinaryFeature binaryFeature = new BinaryFeature(encoder, field, categoryValue);

			putFeature(FieldName.create((field.getName()).getValue() + categoryName), binaryFeature);
		}

		this.fields.add(field);
	}

	private Feature getFeature(FieldName name){
		Feature feature = this.features.get(name);

		if(feature == null){

			if(this.validNames.containsKey(name)){
				feature = this.features.get(this.validNames.get(name));
			}
		}

		return feature;
	}

	private void putFeature(FieldName name, Feature feature){
		FieldName validName = RExpUtil.makeName(name);

		if(!(name).equals(validName)){
			this.validNames.put(validName, name);
		}

		this.features.put(name, feature);
	}

	public RExpEncoder getEncoder(){
		return this.encoder;
	}

	private void setEncoder(RExpEncoder encoder){
		this.encoder = encoder;
	}

	/**
	 * Splits a string by single colon characters (':'), ignoring sequences of two or three colon characters ("::" and ":::").
	 */
	static
	List<String> split(String string){
		List<String> result = new ArrayList<>();

		int pos = 0;

		for(int i = 0; i < string.length(); ){

			if(string.charAt(i) == ':'){
				int delimBegin = i;
				int delimEnd = i;

				while((delimEnd + 1) < string.length() && string.charAt(delimEnd + 1) == ':'){
					delimEnd++;
				}

				if(delimBegin == delimEnd){
					result.add(string.substring(pos, delimBegin));

					pos = (delimEnd + 1);
				}

				i = (delimEnd + 1);
			} else

			{
				i++;
			}
		}

		if(pos <= string.length()){
			result.add(string.substring(pos));
		}

		return result;
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
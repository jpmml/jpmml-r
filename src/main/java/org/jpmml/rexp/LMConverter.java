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

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.regression.RegressionModel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.InteractionFeature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.regression.RegressionModelUtil;

public class LMConverter extends ModelConverter<RGenericVector> {

	private Formula formula = null;


	public LMConverter(RGenericVector lm){
		super(lm);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector lm = getObject();

		RDoubleVector coefficients = (RDoubleVector)lm.getValue("coefficients");
		final
		RGenericVector xlevels = (RGenericVector)lm.getValue("xlevels", true);
		RGenericVector model = (RGenericVector)lm.getValue("model");
		final
		RGenericVector data = (RGenericVector)lm.getValue("data", true);

		RExp terms = model.getAttributeValue("terms");

		RIntegerVector response = (RIntegerVector)terms.getAttributeValue("response");

		FormulaContext context = new FormulaContext(){

			@Override
			public List<String> getCategories(String variable){

				if(xlevels != null && xlevels.hasValue(variable)){
					RStringVector levels = (RStringVector)xlevels.getValue(variable);

					return levels.getValues();
				}

				return null;
			}

			@Override
			public RGenericVector getData(){
				return data;
			}
		};

		this.formula = FormulaUtil.encodeFeatures(context, terms, encoder);

		// Dependent variable
		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			DataField dataField = (DataField)this.formula.getField(responseIndex - 1);

			encoder.setLabel(dataField);
		} else

		{
			throw new IllegalArgumentException();
		}

		// Independent variables
		RStringVector coefficientNames = coefficients.names();
		for(int i = 0; i < coefficientNames.size(); i++){
			String coefficientName = coefficientNames.getValue(i);

			if((LMConverter.INTERCEPT).equals(coefficientName)){
				continue;
			}

			FieldName name = FieldName.create(coefficientName);

			Feature feature;

			List<String> variables = split(coefficientName);
			if(variables.size() == 1){
				feature = this.formula.resolveFeature(name);
			} else

			{
				List<Feature> variableFeatures = new ArrayList<>();

				for(String variable : variables){
					Feature variableFeature = this.formula.resolveFeature(FieldName.create(variable));

					variableFeatures.add(variableFeature);
				}

				feature = new InteractionFeature(encoder, name, DataType.DOUBLE, variableFeatures);
			}

			encoder.addFeature(feature);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector lm = getObject();

		RDoubleVector coefficients = (RDoubleVector)lm.getValue("coefficients");

		Double intercept = coefficients.getValue(LMConverter.INTERCEPT, true);

		List<Feature> features = schema.getFeatures();

		if(coefficients.size() != (features.size() + (intercept != null ? 1 : 0))){
			throw new IllegalArgumentException();
		}

		List<Double> featureCoefficients = getFeatureCoefficients(features, coefficients);

		RegressionModel regressionModel = new RegressionModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema), null)
			.addRegressionTables(RegressionModelUtil.createRegressionTable(features, intercept, featureCoefficients));

		return regressionModel;
	}

	public List<Double> getFeatureCoefficients(List<Feature> features, RDoubleVector coefficients){
		List<Double> result = new ArrayList<>();

		for(Feature feature : features){
			Double coefficient = getFeatureCoefficient(feature, coefficients);

			result.add(coefficient);
		}

		return result;
	}

	public Double getFeatureCoefficient(Feature feature, RDoubleVector coefficients){
		return this.formula.getCoefficient(feature, coefficients);
	}

	/**
	 * Splits a string by single colon characters ('.'), ignoring sequences of two or three colon characters ("::" and ":::").
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

	public static final String INTERCEPT = "(Intercept)";
}
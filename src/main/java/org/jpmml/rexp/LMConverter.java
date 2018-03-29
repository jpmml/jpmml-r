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
import org.dmg.pmml.Model;
import org.jpmml.converter.Feature;
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

		final
		RGenericVector xlevels = (RGenericVector)lm.getValue("xlevels", true);
		final
		RGenericVector model = (RGenericVector)lm.getValue("model");
		final
		RGenericVector data = (RGenericVector)lm.getValue("data", true);

		RExp terms = model.getAttributeValue("terms");

		FormulaContext context = new ModelFrameFormulaContext(model){

			@Override
			public List<String> getCategories(String variable){

				if(xlevels != null && xlevels.hasValue(variable)){
					RStringVector levels = (RStringVector)xlevels.getValue(variable);

					return levels.getValues();
				}

				return super.getCategories(variable);
			}

			@Override
			public RGenericVector getData(){

				if(data != null){
					return data;
				}

				return super.getData();
			}
		};

		encodeSchema(context, terms, encoder);
	}

	public void encodeSchema(FormulaContext context, RExp terms, RExpEncoder encoder){
		RIntegerVector response = (RIntegerVector)terms.getAttributeValue("response");

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		// Dependent variable
		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			DataField dataField = (DataField)formula.getField(responseIndex - 1);

			encoder.setLabel(dataField);
		} else

		{
			throw new IllegalArgumentException();
		}

		String interceptName = getInterceptName();

		// Independent variables
		List<String> coefficientNames = getCoefficientNames();
		for(String coefficientName : coefficientNames){

			if((interceptName).equals(coefficientName)){
				continue;
			}

			Feature feature = formula.resolveFeature(coefficientName);

			encoder.addFeature(feature);
		}

		this.formula = formula;
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector lm = getObject();

		RDoubleVector coefficients = (RDoubleVector)lm.getValue("coefficients");

		Double intercept = coefficients.getValue(getInterceptName(), true);

		List<? extends Feature> features = schema.getFeatures();

		if(coefficients.size() != (features.size() + (intercept != null ? 1 : 0))){
			throw new IllegalArgumentException();
		}

		List<Double> featureCoefficients = getFeatureCoefficients(features, coefficients);

		return RegressionModelUtil.createRegression(features, featureCoefficients, intercept, null, schema);
	}

	public String getInterceptName(){
		return LMConverter.INTERCEPT;
	}

	public List<String> getCoefficientNames(){
		RGenericVector lm = getObject();

		RDoubleVector coefficients = (RDoubleVector)lm.getValue("coefficients");

		RStringVector coefficientNames = coefficients.names();

		return coefficientNames.getDequotedValues();
	}

	public List<Double> getFeatureCoefficients(List<? extends Feature> features, RDoubleVector coefficients){
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

	public static final String INTERCEPT = "(Intercept)";
}
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

		RGenericVector xlevels = lm.getGenericElement("xlevels", true);
		RGenericVector model = lm.getGenericElement("model");
		RGenericVector data = lm.getGenericElement("data", true);

		RExp terms = model.getAttribute("terms");

		FormulaContext context = new ModelFrameFormulaContext(model){

			@Override
			public List<String> getCategories(String variable){

				if(xlevels != null && xlevels.hasElement(variable)){
					RStringVector levels = xlevels.getStringElement(variable);

					return levels.getValues();
				}

				return super.getCategories(variable);
			}

			@Override
			public RVector<?> getData(String variable){

				if(data != null && data.hasElement(variable)){
					return data.getVectorElement(variable);
				}

				return super.getData(variable);
			}
		};

		encodeSchema(terms, context, encoder);
	}

	protected void encodeSchema(RExp terms, FormulaContext context, RExpEncoder encoder){
		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		SchemaUtil.setLabel(formula, terms, null, encoder);

		List<String> names = SchemaUtil.removeSpecialSymbol(getCoefficientNames(), getInterceptName());

		SchemaUtil.addFeatures(formula, names, true, encoder);

		this.formula = formula;
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector lm = getObject();

		RDoubleVector coefficients = lm.getDoubleElement("coefficients");

		Double intercept = coefficients.getElement(getInterceptName(), true);

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

		RDoubleVector coefficients = lm.getDoubleElement("coefficients");

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
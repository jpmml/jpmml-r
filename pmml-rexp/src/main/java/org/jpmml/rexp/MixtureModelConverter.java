/*
 * Copyright (c) 2024 Villu Ruusmann
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
import org.dmg.pmml.Model;
import org.dmg.pmml.regression.RegressionModel;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.Schema;
import org.jpmml.converter.SchemaUtil;
import org.jpmml.converter.regression.RegressionModelUtil;

abstract
public class MixtureModelConverter extends Converter<RGenericVector> {

	private ContinuousLabel label = null;


	public MixtureModelConverter(RGenericVector object){
		super(object);
	}

	protected Model encodeComponent(String name, RExpEncoder encoder){
		RGenericVector object = getObject();

		RDoubleVector coefficients = object.getGenericElement("coefficients").getDoubleElement(name);
		RStringVector dist = object.getGenericElement("dist").getStringElement(name);
		RExp terms = object.getGenericElement("terms").getElement(name);
		RGenericVector model = object.getGenericElement("model");

		RStringVector coefficientNames = coefficients.names();

		FormulaContext context = new ModelFrameFormulaContext(model);

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		switch(name){
			case MixtureModelConverter.NAME_COUNT:
				FormulaUtil.setLabel(formula, terms, null, encoder);

				ContinuousLabel continuousLabel = (ContinuousLabel)encoder.getLabel();

				// XXX
				DataField dataField = (DataField)encoder.getField(continuousLabel.getName());
				dataField.setDataType(DataType.DOUBLE);

				setLabel(new ContinuousLabel(dataField));

				break;
			case MixtureModelConverter.NAME_ZERO:
				break;
			default:
				throw new IllegalArgumentException(name);
		}

		encoder.setLabel(new ContinuousLabel(DataType.DOUBLE));

		List<Feature> features = encoder.getFeatures();
		if(!features.isEmpty()){
			features.clear();
		}

		List<String> names = FormulaUtil.removeSpecialSymbol(coefficientNames.getDequotedValues(), "(Intercept)");

		FormulaUtil.addFeatures(formula, names, true, encoder);

		features = encoder.getFeatures();

		Schema schema = encoder.createSchema();

		Double intercept = coefficients.getElement("(Intercept)", false);

		SchemaUtil.checkSize(coefficients.size() - (intercept != null ? 1 : 0), features);

		List<Double> featureCoefficients = new ArrayList<>();

		for(Feature feature : features){
			Double coefficient = formula.getCoefficient(feature, coefficients);

			featureCoefficients.add(coefficient);
		}

		RegressionModel regressionModel;

		switch(name){
			case MixtureModelConverter.NAME_ZERO:

				switch(dist.asScalar()){
					case "binomial":
						regressionModel = RegressionModelUtil.createRegression(features, featureCoefficients, intercept, RegressionModel.NormalizationMethod.LOGIT, schema);
						break;
					default:
						throw new IllegalArgumentException();
				}
				break;
			case MixtureModelConverter.NAME_COUNT:

				switch(dist.asScalar()){
					case "poisson":
						regressionModel = RegressionModelUtil.createRegression(features, featureCoefficients, intercept, RegressionModel.NormalizationMethod.EXP, schema);
						break;
					default:
						throw new IllegalArgumentException();
				}
				break;
			default:
				throw new IllegalArgumentException(name);
		}

		return regressionModel;
	}

	protected ContinuousLabel getLabel(){
		return this.label;
	}

	private void setLabel(ContinuousLabel label){
		this.label = label;
	}

	protected static final String NAME_COUNT = "count";
	protected static final String NAME_FULL = "full";
	protected static final String NAME_ZERO = "zero";
}
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

import org.dmg.pmml.Apply;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.OpType;
import org.dmg.pmml.general_regression.GeneralRegressionModel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.InteractionFeature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.general_regression.GeneralRegressionModelUtil;

public class EarthConverter extends ModelConverter<RGenericVector> {

	public EarthConverter(RGenericVector earth){
		super(earth);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector earth = getObject();

		RDoubleVector dirs = (RDoubleVector)earth.getValue("dirs");
		RDoubleVector cuts = (RDoubleVector)earth.getValue("cuts");
		RDoubleVector selectedTerms = (RDoubleVector)earth.getValue("selected.terms");
		RDoubleVector coefficients = (RDoubleVector)earth.getValue("coefficients");
		RExp terms = earth.getValue("terms");

		final
		RGenericVector xlevels;

		try {
			xlevels = (RGenericVector)earth.getValue("xlevels");
		} catch(IllegalArgumentException iae){
			throw new IllegalArgumentException("No predictor levels information. Please initialize the \'xlevels\' attribute", iae);
		}

		RStringVector dirsRows = dirs.dimnames(0);
		RStringVector dirsColumns = dirs.dimnames(1);

		RStringVector cutsRows = cuts.dimnames(0);
		RStringVector cutsColumns = cuts.dimnames(1);

		if(!(dirsRows.getValues()).equals(cutsRows.getValues()) || !(dirsColumns.getValues()).equals(cutsColumns.getValues())){
			throw new IllegalArgumentException();
		}

		int rows = dirsRows.size();
		int columns = dirsColumns.size();

		List<String> predictors = dirsColumns.getValues();

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
				return null;
			}
		};

		Formula formula = FormulaUtil.encodeFeatures(context, terms, encoder);

		// Dependent variable
		{
			RStringVector yNames = coefficients.dimnames(1);

			FieldName name = FieldName.create(yNames.asScalar());

			DataField dataField = (DataField)encoder.getField(name);

			encoder.setLabel(dataField);
		}

		// Independent variables
		for(int i = 1; i < selectedTerms.size(); i++){
			int termIndex = ValueUtil.asInt(selectedTerms.getValue(i)) - 1;

			List<Double> dirsRow = RExpUtil.getRow(dirs.getValues(), rows, columns, termIndex);
			List<Double> cutsRow = RExpUtil.getRow(cuts.getValues(), rows, columns, termIndex);

			List<Feature> features = new ArrayList<>();

			predictors:
			for(int j = 0; j < predictors.size(); j++){
				String predictor = predictors.get(j);

				FieldName predictorName = FieldName.create(predictor);

				int dir = ValueUtil.asInt(dirsRow.get(j));
				double cut = cutsRow.get(j);

				Feature feature;

				switch(dir){
					case 0:
						continue predictors;
					case -1:
					case 1:
						{
							FieldName name = FieldName.create(formatHingeFunction(dir, predictorName, cut));

							DerivedField derivedField = encoder.getDerivedField(name);
							if(derivedField == null){
								Apply apply = createHingeFunction(dir, predictorName, cut);

								derivedField = encoder.createDerivedField(name, OpType.CONTINUOUS, DataType.DOUBLE, apply);
							}

							feature = new ContinuousFeature(encoder, derivedField);
						}
						break;
					case 2:
						{
							feature = formula.resolveFeature(predictorName);
						}
						break;
					default:
						throw new IllegalArgumentException();
				}

				features.add(feature);
			}

			Feature feature;

			if(features.size() == 1){
				feature = features.get(0);
			} else

			if(features.size() > 1){
				feature = new InteractionFeature(encoder, FieldName.create(dirsRows.getValue(i)), DataType.DOUBLE, features);
			} else

			{
				throw new IllegalArgumentException();
			}

			encoder.addFeature(feature);
		}
	}

	@Override
	public GeneralRegressionModel encodeModel(Schema schema){
		RGenericVector earth = getObject();

		RDoubleVector coefficients = (RDoubleVector)earth.getValue("coefficients");

		Double intercept = coefficients.getValue(0);

		List<Feature> features = schema.getFeatures();

		if(coefficients.size() != (features.size() + 1)){
			throw new IllegalArgumentException();
		}

		List<Double> featureCoefficients = (coefficients.getValues()).subList(1, features.size() + 1);

		GeneralRegressionModel generalRegressionModel = new GeneralRegressionModel(GeneralRegressionModel.ModelType.GENERALIZED_LINEAR, MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema), null, null, null)
			.setLinkFunction(GeneralRegressionModel.LinkFunction.IDENTITY);

		GeneralRegressionModelUtil.encodeRegressionTable(generalRegressionModel, features, intercept, featureCoefficients, null);

		return generalRegressionModel;
	}

	static
	private String formatHingeFunction(int dir, FieldName name, double cut){

		switch(dir){
			case -1:
				return ("h(" + cut + " - " + name.getValue() + ")");
			case 1:
				return ("h(" + name.getValue() + " - " + cut + ")");
			default:
				throw new IllegalArgumentException();
		}
	}

	static
	private Apply createHingeFunction(int dir, FieldName name, double cut){
		Expression expression;

		switch(dir){
			case -1:
				expression = PMMLUtil.createApply("-", PMMLUtil.createConstant(cut), new FieldRef(name));
				break;
			case 1:
				expression = PMMLUtil.createApply("-", new FieldRef(name), PMMLUtil.createConstant(cut));
				break;
			default:
				throw new IllegalArgumentException();
		}

		return PMMLUtil.createApply("max", expression, PMMLUtil.createConstant(0d));
	}
}
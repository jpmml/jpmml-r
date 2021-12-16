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
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMMLFunctions;
import org.dmg.pmml.general_regression.GeneralRegressionModel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FieldNameUtil;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.InteractionFeature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.SchemaUtil;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.general_regression.GeneralRegressionModelUtil;

public class EarthConverter extends ModelConverter<RGenericVector> {

	public EarthConverter(RGenericVector earth){
		super(earth);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector earth = getObject();

		RDoubleVector dirs = earth.getDoubleElement("dirs");
		RDoubleVector cuts = earth.getDoubleElement("cuts");
		RDoubleVector selectedTerms = earth.getDoubleElement("selected.terms");
		RDoubleVector coefficients = earth.getDoubleElement("coefficients");
		RExp terms = earth.getElement("terms");
		RGenericVector xlevels = DecorationUtil.getGenericElement(earth, "xlevels");

		RStringVector dirsRows = dirs.dimnames(0);
		RStringVector dirsColumns = dirs.dimnames(1);

		RStringVector cutsRows = cuts.dimnames(0);
		RStringVector cutsColumns = cuts.dimnames(1);

		if(!(dirsRows.getValues()).equals(cutsRows.getValues()) || !(dirsColumns.getValues()).equals(cutsColumns.getValues())){
			throw new IllegalArgumentException();
		}

		int rows = dirsRows.size();
		int columns = dirsColumns.size();

		List<String> predictorNames = dirsColumns.getValues();

		FormulaContext context = new XLevelsFormulaContext(xlevels);

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		{
			RStringVector yNames = coefficients.dimnames(1);

			DataField dataField = (DataField)encoder.getField(yNames.asScalar());

			encoder.setLabel(dataField);
		}

		for(int i = 1; i < selectedTerms.size(); i++){
			int termIndex = ValueUtil.asInt(selectedTerms.getValue(i)) - 1;

			List<Double> dirsRow = FortranMatrixUtil.getRow(dirs.getValues(), rows, columns, termIndex);
			List<Double> cutsRow = FortranMatrixUtil.getRow(cuts.getValues(), rows, columns, termIndex);

			List<Feature> features = new ArrayList<>();

			predictors:
			for(int j = 0; j < predictorNames.size(); j++){
				String predictorName = predictorNames.get(j);

				int dir = ValueUtil.asInt(dirsRow.get(j));
				double cut = cutsRow.get(j);

				if(dir == 0){
					continue predictors;
				}

				Feature feature = formula.resolveComplexFeature(predictorName);

				switch(dir){
					case -1:
					case 1:
						{
							ContinuousFeature continuousFeature = feature.toContinuousFeature();

							DerivedField derivedField = encoder.ensureDerivedField(formatHingeFunction(dir, continuousFeature, cut), OpType.CONTINUOUS, DataType.DOUBLE, () -> createHingeFunction(dir, continuousFeature, cut));

							feature = new ContinuousFeature(encoder, derivedField);
						}
						break;
					case 2:
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
				feature = new InteractionFeature(encoder, dirsRows.getValue(i), DataType.DOUBLE, features);
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

		RDoubleVector coefficients = earth.getDoubleElement("coefficients");

		Double intercept = coefficients.getValue(0);

		List<? extends Feature> features = schema.getFeatures();

		SchemaUtil.checkSize(coefficients.size() - 1, features);

		List<Double> featureCoefficients = (coefficients.getValues()).subList(1, features.size() + 1);

		GeneralRegressionModel generalRegressionModel = new GeneralRegressionModel(GeneralRegressionModel.ModelType.GENERALIZED_LINEAR, MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema.getLabel()), null, null, null)
			.setLinkFunction(GeneralRegressionModel.LinkFunction.IDENTITY);

		GeneralRegressionModelUtil.encodeRegressionTable(generalRegressionModel, features, featureCoefficients, intercept, null);

		return generalRegressionModel;
	}

	static
	private String formatHingeFunction(int dir, Feature feature, double cut){

		switch(dir){
			case -1:
				return FieldNameUtil.create("h", cut + " - " + feature.getName());
			case 1:
				return FieldNameUtil.create("h", feature.getName() + " - " + cut);
			default:
				throw new IllegalArgumentException();
		}
	}

	static
	private Apply createHingeFunction(int dir, Feature feature, double cut){
		Expression expression;

		switch(dir){
			case -1:
				expression = PMMLUtil.createApply(PMMLFunctions.SUBTRACT, PMMLUtil.createConstant(cut), feature.ref());
				break;
			case 1:
				expression = PMMLUtil.createApply(PMMLFunctions.SUBTRACT, feature.ref(), PMMLUtil.createConstant(cut));
				break;
			default:
				throw new IllegalArgumentException();
		}

		return PMMLUtil.createApply(PMMLFunctions.MAX, expression, PMMLUtil.createConstant(0d));
	}
}

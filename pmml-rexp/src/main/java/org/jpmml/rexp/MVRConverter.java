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

import java.util.List;

import org.dmg.pmml.Apply;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMMLFunctions;
import org.dmg.pmml.general_regression.GeneralRegressionModel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ExpressionUtil;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FieldNameUtil;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.SchemaUtil;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.general_regression.GeneralRegressionModelUtil;

public class MVRConverter extends ModelConverter<RGenericVector> {

	public MVRConverter(RGenericVector mvr){
		super(mvr);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector mvr = getObject();

		RDoubleVector coefficients = mvr.getDoubleElement("coefficients");
		RExp terms = mvr.getElement("terms");
		RGenericVector model = mvr.getGenericElement("model");

		RStringVector rowNames = coefficients.dimnames(0);
		RStringVector columnNames = coefficients.dimnames(1);

		FormulaContext context = new ModelFrameFormulaContext(model);

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		{
			String name = columnNames.asScalar();

			DataField dataField = (DataField)encoder.getField(name);

			encoder.setLabel(dataField);
		}

		FormulaUtil.addFeatures(formula, rowNames, true, encoder);

		scaleFeatures(encoder);
	}

	@Override
	public GeneralRegressionModel encodeModel(Schema schema){
		RGenericVector mvr = getObject();

		RDoubleVector coefficients = mvr.getDoubleElement("coefficients");
		RDoubleVector xMeans = mvr.getDoubleElement("Xmeans");
		RDoubleVector yMeans = mvr.getDoubleElement("Ymeans");
		RNumberVector<?> ncomp = mvr.getNumericElement("ncomp");

		RStringVector rowNames = coefficients.dimnames(0);
		RStringVector columnNames = coefficients.dimnames(1);
		RStringVector compNames = coefficients.dimnames(2);

		int rows = rowNames.size();
		int columns = columnNames.size();
		int components = compNames.size();

		List<? extends Feature> features = schema.getFeatures();

		List<Double> featureCoefficients = FortranMatrixUtil.getColumn(coefficients.getValues(), rows, (columns * components), 0 + (ValueUtil.asInt(ncomp.asScalar()) - 1));

		Double intercept = yMeans.getValue(0);

		for(int j = 0; j < rowNames.size(); j++){
			intercept -= (featureCoefficients.get(j) * xMeans.getValue(j));
		}

		GeneralRegressionModel generalRegressionModel = new GeneralRegressionModel(GeneralRegressionModel.ModelType.GENERALIZED_LINEAR, MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema), null, null, null)
			.setLinkFunction(GeneralRegressionModel.LinkFunction.IDENTITY);

		GeneralRegressionModelUtil.encodeRegressionTable(generalRegressionModel, features, featureCoefficients, intercept, null);

		return generalRegressionModel;
	}

	private void scaleFeatures(RExpEncoder encoder){
		RGenericVector mvr = getObject();

		RDoubleVector scale = mvr.getDoubleElement("scale", false);
		if(scale == null){
			return;
		}

		List<Feature> features = encoder.getFeatures();

		SchemaUtil.checkSize(scale.size(), features);

		for(int i = 0; i < features.size(); i++){
			Feature feature = features.get(i);
			Double factor = scale.getValue(i);

			if(ValueUtil.isOne(factor)){
				continue;
			}

			ContinuousFeature continuousFeature = feature.toContinuousFeature();

			Apply apply = ExpressionUtil.createApply(PMMLFunctions.DIVIDE, continuousFeature.ref(), ExpressionUtil.createConstant(factor));

			DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("scale", continuousFeature), OpType.CONTINUOUS, DataType.DOUBLE, apply);

			features.set(i, new ContinuousFeature(encoder, derivedField));
		}
	}
}
/*
 * Copyright (c) 2018 Villu Ruusmann
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

import org.dmg.pmml.DataType;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.converter.CMatrixUtil;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.regression.RegressionModelUtil;

public class MultinomConverter extends ModelConverter<RGenericVector> {

	public MultinomConverter(RGenericVector multinom){
		super(multinom);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector multinom = getObject();

		RStringVector lev = (RStringVector)multinom.getValue("lev");
		RExp terms = multinom.getValue("terms");
		RGenericVector xlevels = (RGenericVector)multinom.getValue("xlevels");
		RStringVector vcoefnames = (RStringVector)multinom.getValue("vcoefnames");

		FormulaContext context = new XLevelsFormulaContext(xlevels);

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		// Dependent variable
		SchemaUtil.setLabel(formula, terms, lev, encoder);

		List<String> names = SchemaUtil.removeSpecialSymbol(vcoefnames.getValues(), "(Intercept)", 0);

		// Independent variables
		SchemaUtil.addFeatures(formula, names, true, encoder);
	}

	@Override
	public RegressionModel encodeModel(Schema schema){
		RGenericVector multinom = getObject();

		RDoubleVector n = (RDoubleVector)multinom.getValue("n");
		RBooleanVector softmax = (RBooleanVector)multinom.getValue("softmax");
		RBooleanVector censored = (RBooleanVector)multinom.getValue("censored");
		RDoubleVector wts = (RDoubleVector)multinom.getValue("wts");

		if(n.size() != 3){
			throw new IllegalArgumentException();
		}

		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		if(categoricalLabel.size() == 2){

			if(wts.size() != (1 + (features.size() + 1))){
				throw new IllegalArgumentException();
			}

			int offset = 1;

			List<Double> coefficients = (wts.getValues()).subList(offset + 1, offset + 1 + features.size());
			Double intercept = wts.getValue(offset);

			return RegressionModelUtil.createBinaryLogisticClassification(features, coefficients, intercept, RegressionModel.NormalizationMethod.LOGIT, true, schema);
		} else

		if(categoricalLabel.size() > 2){

			if(wts.size() != categoricalLabel.size() * (1 + (features.size() + 1))){
				throw new IllegalArgumentException();
			} // End if

			if(softmax != null && softmax.asScalar()){

				if(censored != null && censored.asScalar()){
					throw new IllegalArgumentException();
				}
			}

			List<RegressionTable> regressionTables = new ArrayList<>();

			{
				RegressionTable regressionTable = new RegressionTable(0d)
					.setTargetCategory(categoricalLabel.getValue(0));

				regressionTables.add(regressionTable);
			}

			for(int i = 1; i < categoricalLabel.size(); i++){
				List<Double> categoryWts = CMatrixUtil.getRow(wts.getValues(), categoricalLabel.size(), 1 + (features.size() + 1), i);

				List<Double> coefficients = categoryWts.subList(1 + 1, 1 + 1 + features.size());
				Double intercept = categoryWts.get(1);

				RegressionTable regressionTable = RegressionModelUtil.createRegressionTable(features, coefficients, intercept)
					.setTargetCategory(categoricalLabel.getValue(i));

				regressionTables.add(regressionTable);
			}

			RegressionModel regressionModel = new RegressionModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel), regressionTables)
				.setNormalizationMethod(RegressionModel.NormalizationMethod.SOFTMAX)
				.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));

			return regressionModel;
		} else

		{
			throw new IllegalArgumentException();
		}
	}
}
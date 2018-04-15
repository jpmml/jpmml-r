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

import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.dmg.pmml.DataType;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.regression.RegressionModelUtil;

public class MultNetConverter extends GLMNetConverter {

	public MultNetConverter(RGenericVector multnet){
		super(multnet);
	}

	@Override
	public Model encodeModel(RDoubleVector a0, RExp beta, int column, Schema schema){
		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		RIntegerVector a0Dim = a0.dim();

		int a0Rows = a0Dim.getValue(0);
		int a0Columns = a0Dim.getValue(1);

		RGenericVector categoryBetas = (RGenericVector)beta;

		if(categoricalLabel.size() == 2){
			List<Double> categoryA0 = FortranMatrixUtil.getRow(a0.getValues(), a0Rows, a0Columns, 1);
			S4Object categoryBeta = (S4Object)categoryBetas.getValue(1);

			Function<Double, Double> function = new Function<Double, Double>(){

				@Override
				public Double apply(Double value){
					return 2d * value.doubleValue();
				}
			};

			Double intercept = function.apply(categoryA0.get(column));
			List<Double> coefficients = Lists.transform(getCoefficients(categoryBeta, column), function);

			return RegressionModelUtil.createBinaryLogisticClassification(schema.getFeatures(), coefficients, intercept, RegressionModel.NormalizationMethod.LOGIT, true, schema);
		} else

		if(categoricalLabel.size() > 2){
			RegressionModel regressionModel = new RegressionModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel), null)
				.setNormalizationMethod(RegressionModel.NormalizationMethod.SOFTMAX)
				.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));

			for(int i = 0; i < categoricalLabel.size(); i++){
				String targetCategory = categoricalLabel.getValue(i);

				List<Double> categoryA0 = FortranMatrixUtil.getRow(a0.getValues(), a0Rows, a0Columns, i);
				S4Object categoryBeta = (S4Object)categoryBetas.getValue(targetCategory);

				Double intercept = categoryA0.get(column);
				List<Double> coefficients = getCoefficients(categoryBeta, column);

				RegressionTable regressionTable = RegressionModelUtil.createRegressionTable(schema.getFeatures(), coefficients, intercept)
					.setTargetCategory(targetCategory);

				regressionModel.addRegressionTables(regressionTable);
			}

			return regressionModel;
		} else

		{
			throw new IllegalArgumentException();
		}
	}
}
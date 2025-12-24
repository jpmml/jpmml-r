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

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.Label;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.mining.MiningModelUtil;

public class AdaConverter extends RPartEnsembleConverter<RGenericVector> {

	public AdaConverter(RGenericVector ada){
		super(ada);
	}

	@Override
	public RPartConverter createConverter(RGenericVector rpart){
		return new RPartConverter(rpart){

			@Override
			public boolean hasScoreDistribution(){
				return false;
			}

			@Override
			public TreeModel encodeModel(Schema schema){
				TreeModel treeModel = super.encodeModel(schema)
					.setMiningFunction(MiningFunction.REGRESSION);

				return treeModel;
			}
		};
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector ada = getObject();

		if(ada.hasElement("terms")){
			encodeFormula(encoder);
		} else

		{
			encodeNonFormula(encoder);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector ada = getObject();

		RGenericVector model = ada.getGenericElement("model");

		RGenericVector trees = model.getGenericElement("trees");
		RDoubleVector alpha = model.getDoubleElement("alpha");

		List<TreeModel> treeModels = encodeTreeModels(trees);

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema((Label)null))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.WEIGHTED_SUM, Segmentation.MissingPredictionTreatment.RETURN_MISSING, treeModels, alpha.getValues()))
			.setOutput(ModelUtil.createPredictedOutput("adaValue", OpType.CONTINUOUS, DataType.DOUBLE));

		return MiningModelUtil.createBinaryLogisticClassification(miningModel, 2d, 0d, RegressionModel.NormalizationMethod.LOGIT, true, schema);
	}

	private void encodeFormula(RExpEncoder encoder){
		RGenericVector ada = getObject();

		RGenericVector model = ada.getGenericElement("model");
		RExp terms = ada.getElement("terms");
		RIntegerVector fit = ada.getIntegerElement("fit");

		RGenericVector trees = model.getGenericElement("trees");

		RExpEncoder termsEncoder = new RExpEncoder();

		FormulaContext context = new EmptyFormulaContext();

		Formula formula = FormulaUtil.createFormula(terms, context, termsEncoder);

		FormulaUtil.setLabel(formula, terms, fit, encoder);

		encodeTreeSchemas(trees, encoder);
	}

	private void encodeNonFormula(RExpEncoder encoder){
		RGenericVector ada = getObject();

		RGenericVector model = ada.getGenericElement("model");
		RFactorVector fit = ada.getFactorElement("fit");

		RGenericVector trees = model.getGenericElement("trees");

		DataField dataField = encoder.createDataField("_target", OpType.CATEGORICAL, DataType.STRING, fit.getLevelValues());

		encoder.setLabel(dataField);

		encodeTreeSchemas(trees, encoder);
	}
}
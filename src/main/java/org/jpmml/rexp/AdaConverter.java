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
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Visitor;
import org.dmg.pmml.VisitorAction;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.SigmoidTransformation;
import org.jpmml.converter.mining.MiningModelUtil;

public class AdaConverter extends RPartEnsembleConverter<RGenericVector> {

	public AdaConverter(RGenericVector ada){
		super(ada);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector ada = getObject();

		RExp terms = ada.getValue("terms", true);

		if(terms != null){
			encodeFormula(encoder);
		} else

		{
			encodeNonFormula(encoder);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector ada = getObject();

		RGenericVector model = (RGenericVector)ada.getValue("model");

		RGenericVector trees = (RGenericVector)model.getValue("trees");
		RDoubleVector alpha = (RDoubleVector)model.getValue("alpha");

		List<TreeModel> treeModels = encodeTreeModels(trees);

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(null))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.WEIGHTED_SUM, treeModels, alpha.getValues()))
			.setOutput(ModelUtil.createPredictedOutput(FieldName.create("adaValue"), OpType.CONTINUOUS, DataType.DOUBLE, new SigmoidTransformation(-2d)));

		return MiningModelUtil.createBinaryLogisticClassification(miningModel, 1d, 0d, RegressionModel.NormalizationMethod.NONE, true, schema);
	}

	@Override
	protected Visitor getTreeModelTransformer(){
		return new TreeModelTransformer(){

			@Override
			public VisitorAction visit(TreeModel treeModel){
				treeModel.setMiningFunction(MiningFunction.REGRESSION);

				return super.visit(treeModel);
			}
		};
	}

	private void encodeFormula(RExpEncoder encoder){
		RGenericVector ada = getObject();

		RGenericVector model = (RGenericVector)ada.getValue("model");
		RExp terms = ada.getValue("terms");
		RIntegerVector fit = (RIntegerVector)ada.getValue("fit");

		RGenericVector trees = (RGenericVector)model.getValue("trees");

		RExpEncoder termsEncoder = new RExpEncoder();

		FormulaContext context = new EmptyFormulaContext();

		Formula formula = FormulaUtil.createFormula(terms, context, termsEncoder);

		SchemaUtil.setLabel(formula, terms, fit, encoder);

		encodeTreeSchemas(trees, encoder);
	}

	private void encodeNonFormula(RExpEncoder encoder){
		RGenericVector ada = getObject();

		RGenericVector model = (RGenericVector)ada.getValue("model");
		RIntegerVector fit = (RIntegerVector)ada.getValue("fit");

		RGenericVector trees = (RGenericVector)model.getValue("trees");

		DataField dataField = encoder.createDataField(FieldName.create("_target"), OpType.CATEGORICAL, DataType.STRING, RExpUtil.getFactorLevels(fit));

		encoder.setLabel(dataField);

		encodeTreeSchemas(trees, encoder);
	}
}
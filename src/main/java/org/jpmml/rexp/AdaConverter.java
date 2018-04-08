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
import java.util.Arrays;
import java.util.List;

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.Visitor;
import org.dmg.pmml.VisitorAction;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.Label;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.SigmoidTransformation;
import org.jpmml.converter.mining.MiningModelUtil;
import org.jpmml.model.visitors.AbstractVisitor;

public class AdaConverter extends TreeModelConverter<RGenericVector> {

	private List<Schema> schemas = new ArrayList<>();


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

		List<TreeModel> treeModels = new ArrayList<>();

		// Transforms classification-type tree models to regression-type tree models
		Visitor treeModelTransformer = new AbstractVisitor(){

			@Override
			public VisitorAction visit(TreeModel treeModel){
				treeModel
					.setMiningFunction(MiningFunction.REGRESSION)
					.setOutput(null);

				return super.visit(treeModel);
			}

			@Override
			public VisitorAction visit(Node node){

				if(node.hasScoreDistributions()){
					List<ScoreDistribution> scoreDistributions = node.getScoreDistributions();

					scoreDistributions.clear();
				}

				return super.visit(node);
			}
		};

		for(int i = 0; i < trees.size(); i++){
			RGenericVector rpart = (RGenericVector)trees.getValue(i);

			RPartConverter treeConverter = new RPartConverter(rpart);

			Schema segmentSchema = this.schemas.get(i);

			TreeModel treeModel = treeConverter.encodeModel(segmentSchema);

			treeModelTransformer.applyTo(treeModel);

			treeModels.add(treeModel);
		}

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(null))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.WEIGHTED_SUM, treeModels, alpha.getValues()))
			.setOutput(ModelUtil.createPredictedOutput(FieldName.create("adaValue"), OpType.CONTINUOUS, DataType.DOUBLE, new SigmoidTransformation(-2d)));

		return MiningModelUtil.createBinaryLogisticClassification(miningModel, 1d, 0d, RegressionModel.NormalizationMethod.NONE, true, schema);
	}

	private void encodeFormula(RExpEncoder encoder){
		RGenericVector ada = getObject();

		RExp terms = ada.getValue("terms");
		RIntegerVector fit = (RIntegerVector)ada.getValue("fit");

		RIntegerVector response = (RIntegerVector)terms.getAttributeValue("response");

		RExpEncoder termsEncoder = new RExpEncoder();

		FormulaContext formulaContext = new FormulaContext(){

			@Override
			public List<String> getCategories(String variable){
				return null;
			}

			@Override
			public RGenericVector getData(){
				return null;
			}
		};

		Formula formula = FormulaUtil.createFormula(terms, formulaContext, termsEncoder);

		// Dependent variable
		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			DataField dataField = (DataField)formula.getField(responseIndex - 1);

			encoder.addDataField(dataField);

			dataField = (DataField)encoder.toCategorical(dataField.getName(), RExpUtil.getFactorLevels(fit));

			encoder.setLabel(dataField);
		} else

		{
			throw new IllegalArgumentException();
		}

		encodeTreeSchemas(encoder);
	}

	private void encodeNonFormula(RExpEncoder encoder){
		RGenericVector ada = getObject();

		RIntegerVector fit = (RIntegerVector)ada.getValue("fit");

		DataField dataField = encoder.createDataField(FieldName.create("_target"), OpType.CATEGORICAL, DataType.STRING, RExpUtil.getFactorLevels(fit));

		encoder.setLabel(dataField);

		encodeTreeSchemas(encoder);
	}

	private void encodeTreeSchemas(RExpEncoder encoder){
		RGenericVector ada = getObject();

		RGenericVector model = (RGenericVector)ada.getValue("model");

		RGenericVector trees = (RGenericVector)model.getValue("trees");

		Label signLabel = new CategoricalLabel(null, DataType.INTEGER, Arrays.asList("-1", "1"));

		for(int i = 0; i < trees.size(); i++){
			RGenericVector rpart = (RGenericVector)trees.getValue(i);

			RPartConverter treeConverter = new RPartConverter(rpart);

			RExpEncoder treeEncoder = new RExpEncoder();

			treeConverter.encodeSchema(treeEncoder);

			encoder.addFields(treeEncoder);

			Schema segmentSchema = new Schema(signLabel, treeEncoder.getFeatures());

			this.schemas.add(segmentSchema);
		}
	}
}
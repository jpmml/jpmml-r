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
import org.dmg.pmml.Model;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.Visitor;
import org.dmg.pmml.VisitorAction;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.Label;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.mining.MiningModelUtil;
import org.jpmml.model.visitors.AbstractVisitor;

public class BaggingConverter extends ModelConverter<RGenericVector> {

	private List<Schema> schemas = new ArrayList<>();


	public BaggingConverter(RGenericVector bagging){
		super(bagging);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector bagging = getObject();

		RGenericVector trees = (RGenericVector)bagging.getValue("trees");
		RExp terms = bagging.getValue("terms");
		RIntegerVector vardepSummary = (RIntegerVector)bagging.getAttributeValue("vardep.summary");

		RExpEncoder termsEncoder = new RExpEncoder();

		FormulaContext content = new EmptyFormulaContext();

		Formula formula = FormulaUtil.createFormula(terms, content, termsEncoder);

		SchemaUtil.setLabel(formula, terms, vardepSummary.names(), encoder);

		Label label = encoder.getLabel();

		for(int i = 0; i < trees.size(); i++){
			RGenericVector rpart = (RGenericVector)trees.getValue(i);

			RPartConverter treeConverter = new RPartConverter(rpart);

			RExpEncoder treeEncoder = new RExpEncoder();

			treeConverter.encodeSchema(treeEncoder);

			encoder.addFields(treeEncoder);

			Schema segmentSchema = new Schema(label.toAnonymousLabel(), treeEncoder.getFeatures());

			this.schemas.add(segmentSchema);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector bagging = getObject();

		RGenericVector trees = (RGenericVector)bagging.getValue("trees");

		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		List<TreeModel> treeModels = new ArrayList<>();

		Visitor treeModelTransformer = new AbstractVisitor(){

			@Override
			public VisitorAction visit(TreeModel treeModel){
				treeModel.setOutput(null);

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

		MiningModel miningModel = new MiningModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.MAJORITY_VOTE, treeModels))
			.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));

		return miningModel;
	}
}
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

import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunctionType;
import org.dmg.pmml.MiningModel;
import org.dmg.pmml.MultipleModelMethodType;
import org.dmg.pmml.Node;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.TreeModel;
import org.dmg.pmml.True;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ListFeature;
import org.jpmml.converter.MiningModelUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;

public class RangerConverter extends TreeModelConverter<RGenericVector> {

	public RangerConverter(RGenericVector ranger){
		super(ranger);
	}

	@Override
	public void encodeFeatures(FeatureMapper featureMapper){
		RGenericVector ranger = getObject();

		RGenericVector forest;

		try {
			forest = (RGenericVector)ranger.getValue("forest");
		} catch(IllegalArgumentException iae){
			throw new IllegalArgumentException("No forest information. Please initialize the \'forest\' attribute", iae);
		}

		RGenericVector variableLevels;

		try {
			variableLevels = (RGenericVector)ranger.getValue("variable.levels");
		} catch(IllegalArgumentException iae){
			throw new IllegalArgumentException("No variable levels information. Please initialize the \'forest$variable.levels\' attribute", iae);
		}

		RNumberVector<?> predictions = (RNumberVector<?>)ranger.getValue("predictions");

		// Dependent variable
		{
			FieldName name = FieldName.create("_target");

			if(predictions instanceof RIntegerVector){
				RIntegerVector factor = (RIntegerVector)predictions;

				featureMapper.append(name, factor.getLevelValues());
			} else

			{
				featureMapper.append(name, false);
			}
		}

		RBooleanVector isOrdered = (RBooleanVector)forest.getValue("is.ordered");
		RStringVector independentVariableNames = (RStringVector)forest.getValue("independent.variable.names");

		// Independent variables
		for(int i = 0; i < independentVariableNames.size(); i++){

			if(!isOrdered.getValue(i + 1)){
				throw new IllegalArgumentException();
			}

			String independentVariableName = independentVariableNames.getValue(i);

			FieldName name = FieldName.create(independentVariableName);

			RStringVector levels = (RStringVector)variableLevels.getValue(independentVariableName);
			if(levels != null){
				featureMapper.append(name, levels.getValues());
			} else

			{
				featureMapper.append(name, false);
			}
		}
	}

	@Override
	public MiningModel encodeModel(Schema schema){
		RGenericVector ranger = getObject();

		RStringVector treetype = (RStringVector)ranger.getValue("treetype");

		switch(treetype.asScalar()){
			case "Regression":
				return encodeRegression(ranger, schema);
			case "Classification":
				return encodeClassification(ranger, schema);
			default:
				throw new IllegalArgumentException();
		}
	}

	private MiningModel encodeRegression(RGenericVector ranger, Schema schema){
		RGenericVector forest = (RGenericVector)ranger.getValue("forest");

		ScoreEncoder scoreEncoder = new ScoreEncoder(){

			@Override
			public String encode(Number value){
				return ValueUtil.formatValue(value);
			}
		};

		List<TreeModel> treeModels = encodeForest(forest, MiningFunctionType.REGRESSION, scoreEncoder, schema);

		MiningModel miningModel = new MiningModel(MiningFunctionType.REGRESSION, ModelUtil.createMiningSchema(schema))
			.setSegmentation(MiningModelUtil.createSegmentation(MultipleModelMethodType.AVERAGE, treeModels));

		return miningModel;
	}

	private MiningModel encodeClassification(RGenericVector ranger, Schema schema){
		RGenericVector forest = (RGenericVector)ranger.getValue("forest");

		final
		RStringVector levels = (RStringVector)forest.getValue("levels");

		ScoreEncoder scoreEncoder = new ScoreEncoder(){

			@Override
			public String encode(Number value){
				int index = ValueUtil.asInt(value);

				return levels.getValue(index - 1);
			}
		};

		List<TreeModel> treeModels = encodeForest(forest, MiningFunctionType.CLASSIFICATION, scoreEncoder, schema);

		MiningModel miningModel = new MiningModel(MiningFunctionType.CLASSIFICATION, ModelUtil.createMiningSchema(schema))
			.setSegmentation(MiningModelUtil.createSegmentation(MultipleModelMethodType.MAJORITY_VOTE, treeModels));

		return miningModel;
	}

	private List<TreeModel> encodeForest(RGenericVector forest, MiningFunctionType miningFunction, ScoreEncoder scoreEncoder, Schema schema){
		RNumberVector<?> numTrees = (RNumberVector<?>)forest.getValue("num.trees");
		RGenericVector childNodeIDs = (RGenericVector)forest.getValue("child.nodeIDs");
		RGenericVector splitVarIDs = (RGenericVector)forest.getValue("split.varIDs");
		RGenericVector splitValues = (RGenericVector)forest.getValue("split.values");

		Schema segmentSchema = schema.toAnonymousSchema();

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < ValueUtil.asInt(numTrees.asScalar()); i++){
			TreeModel treeModel = encodeTreeModel(miningFunction, scoreEncoder, (RGenericVector)childNodeIDs.getValue(i), (RNumberVector<?>)splitVarIDs.getValue(i), (RNumberVector<?>)splitValues.getValue(i), segmentSchema);

			treeModels.add(treeModel);
		}

		return treeModels;
	}

	private TreeModel encodeTreeModel(MiningFunctionType miningFunction, ScoreEncoder scoreEncoder, RGenericVector childNodeIDs, RNumberVector<?> splitVarIDs, RNumberVector<?> splitValues, Schema schema){
		RNumberVector<?> leftChildIDs = (RNumberVector<?>)childNodeIDs.getValue(0);
		RNumberVector<?> rightChildIDs = (RNumberVector<?>)childNodeIDs.getValue(1);

		Node root = new Node()
			.setPredicate(new True());

		encodeNode(root, 0, scoreEncoder, leftChildIDs, rightChildIDs, splitVarIDs, splitValues, schema);

		TreeModel treeModel = new TreeModel(miningFunction, ModelUtil.createMiningSchema(schema), root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	private void encodeNode(Node node, int index, ScoreEncoder scoreEncoder, RNumberVector<?> leftChildIDs, RNumberVector<?> rightChildIDs, RNumberVector<?> splitVarIDs, RNumberVector<?> splitValues, Schema schema){
		int leftIndex = ValueUtil.asInt(leftChildIDs.getValue(index));
		int rightIndex = ValueUtil.asInt(rightChildIDs.getValue(index));

		Number splitValue = splitValues.getValue(index);

		if(leftIndex == 0 && rightIndex == 0){
			node.setScore(scoreEncoder.encode(splitValue));

			return;
		}

		Predicate leftPredicate;
		Predicate rightPredicate;

		int splitVarIndex = ValueUtil.asInt(splitVarIDs.getValue(index));

		Feature feature = schema.getFeature(splitVarIndex - 1);

		if(feature instanceof ListFeature){
			ListFeature listFeature = (ListFeature)feature;

			int splitLevelIndex = ValueUtil.asInt(splitValue);

			List<String> values = listFeature.getValues();

			leftPredicate = createSimpleSetPredicate(listFeature, values.subList(0, splitLevelIndex));
			rightPredicate = createSimpleSetPredicate(listFeature, values.subList(splitLevelIndex, values.size()));
		} else

		if(feature instanceof ContinuousFeature){
			String value = ValueUtil.formatValue(splitValue);

			leftPredicate = createSimplePredicate(feature, SimplePredicate.Operator.LESS_OR_EQUAL, value);
			rightPredicate = createSimplePredicate(feature, SimplePredicate.Operator.GREATER_THAN, value);
		} else

		{
			throw new IllegalArgumentException();
		}

		Node leftChild = new Node()
			.setPredicate(leftPredicate);

		encodeNode(leftChild, leftIndex, scoreEncoder, leftChildIDs, rightChildIDs, splitVarIDs, splitValues, schema);

		Node rightChild = new Node()
			.setPredicate(rightPredicate);

		encodeNode(rightChild, rightIndex, scoreEncoder, leftChildIDs, rightChildIDs, splitVarIDs, splitValues, schema);

		node.addNodes(leftChild, rightChild);
	}

	static
	private interface ScoreEncoder {

		String encode(Number value);
	}
}
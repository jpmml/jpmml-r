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
import java.util.stream.Collectors;

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.tree.BranchNode;
import org.dmg.pmml.tree.ClassifierNode;
import org.dmg.pmml.tree.LeafNode;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.CategoryManager;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelEncoder;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.mining.MiningModelUtil;

public class RangerConverter extends TreeModelConverter<RGenericVector> {

	boolean hasDependentVar = false;


	public RangerConverter(RGenericVector ranger){
		super(ranger);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector ranger = getObject();

		RGenericVector forest = ranger.getGenericElement("forest", false);
		if(forest == null){
			throw new IllegalArgumentException("Missing \'forest\' element. Please re-train the model object with \'write.forest\' argument set to TRUE");
		}

		RStringVector treeType = ranger.getStringElement("treetype");
		RGenericVector variableLevels = DecorationUtil.getGenericElement(ranger, "variable.levels");

		{
			FieldName name = FieldName.create("_target");

			DataField dataField;

			switch(treeType.asScalar()){
				case "Regression":
					{
						dataField = encoder.createDataField(name, OpType.CONTINUOUS, DataType.DOUBLE);
					}
					break;
				case "Classification":
				case "Probability estimation":
					{
						RStringVector levels = forest.getStringElement("levels");

						dataField = encoder.createDataField(name, OpType.CATEGORICAL, null, levels.getValues());
					}
					break;
				default:
					throw new IllegalArgumentException();
			}

			encoder.setLabel(dataField);
		}

		RBooleanVector isOrdered = forest.getBooleanElement("is.ordered");
		RStringVector independentVariableNames = forest.getStringElement("independent.variable.names");

		this.hasDependentVar = (isOrdered.size() == (independentVariableNames.size() + 1));

		for(int i = 0; i < independentVariableNames.size(); i++){

			if(!isOrdered.getValue(this.hasDependentVar ? (i + 1) : i)){
				throw new IllegalArgumentException();
			}

			String independentVariableName = independentVariableNames.getValue(i);

			FieldName name = FieldName.create(independentVariableName);

			DataField dataField;

			if(variableLevels.hasElement(independentVariableName)){
				RStringVector levels = variableLevels.getStringElement(independentVariableName);

				dataField = encoder.createDataField(name, OpType.CATEGORICAL, DataType.STRING, levels.getValues());
			} else

			{
				dataField = encoder.createDataField(name, OpType.CONTINUOUS, DataType.DOUBLE);
			}

			encoder.addFeature(dataField);
		}
	}

	@Override
	public MiningModel encodeModel(Schema schema){
		RGenericVector ranger = getObject();

		RStringVector treeType = ranger.getStringElement("treetype");
		RGenericVector forest = ranger.getGenericElement("forest");

		MiningModel miningModel;

		switch(treeType.asScalar()){
			case "Regression":
				miningModel = encodeRegression(forest, schema);
				break;
			case "Classification":
				miningModel = encodeClassification(forest, schema);
				break;
			case "Probability estimation":
				miningModel = encodeProbabilityForest(forest, schema);
				break;
			default:
				throw new IllegalArgumentException();
		}

		RStringVector importanceMode = ranger.getStringElement("importance.mode", false);
		RDoubleVector variableImportance = ranger.getDoubleElement("variable.importance", false);

		if(variableImportance != null){
			ModelEncoder encoder = (ModelEncoder)schema.getEncoder();

			for(int i = 0; i < variableImportance.size(); i++){
				encoder.addFeatureImportance(miningModel, schema.getFeature(i), variableImportance.getValue(i));
			}
		}

		return miningModel;
	}

	private MiningModel encodeRegression(RGenericVector forest, Schema schema){
		ScoreEncoder scoreEncoder = new ScoreEncoder(){

			@Override
			public Node encode(Node node, Number splitValue, RNumberVector<?> terminalClassCount){
				node.setScore(splitValue);

				return node;
			}
		};

		List<TreeModel> treeModels = encodeForest(forest, MiningFunction.REGRESSION, scoreEncoder, schema);

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema.getLabel()))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.AVERAGE, treeModels));

		return miningModel;
	}

	private MiningModel encodeClassification(RGenericVector forest, Schema schema){
		RStringVector levels = forest.getStringElement("levels");

		ScoreEncoder scoreEncoder = new ScoreEncoder(){

			@Override
			public Node encode(Node node, Number splitValue, RNumberVector<?> terminalClassCount){
				int index = ValueUtil.asInt(splitValue);

				if(terminalClassCount != null){
					throw new IllegalArgumentException();
				}

				node.setScore(levels.getValue(index - 1));

				return node;
			}
		};

		List<TreeModel> treeModels = encodeForest(forest, MiningFunction.CLASSIFICATION, scoreEncoder, schema);

		MiningModel miningModel = new MiningModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(schema.getLabel()))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.MAJORITY_VOTE, treeModels));

		return miningModel;
	}

	private MiningModel encodeProbabilityForest(RGenericVector forest, Schema schema){
		RStringVector levels = forest.getStringElement("levels");

		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		ScoreEncoder scoreEncoder = new ScoreEncoder(){

			@Override
			public Node encode(Node node, Number splitValue, RNumberVector<?> terminalClassCount){

				if(splitValue.doubleValue() != 0d || (terminalClassCount == null || terminalClassCount.size() != levels.size())){
					throw new IllegalArgumentException();
				}

				node = new ClassifierNode(node);

				List<ScoreDistribution> scoreDistributions = node.getScoreDistributions();

				Number maxProbability = null;

				for(int i = 0; i < terminalClassCount.size(); i++){
					String value = levels.getValue(i);
					Number probability = terminalClassCount.getValue(i);

					if(maxProbability == null || ((Comparable)maxProbability).compareTo(probability) < 0){
						node.setScore(value);

						maxProbability = probability;
					}

					ScoreDistribution scoreDistribution = new ScoreDistribution(value, probability);

					scoreDistributions.add(scoreDistribution);
				}

				return node;
			}
		};

		List<TreeModel> treeModels = encodeForest(forest, MiningFunction.CLASSIFICATION, scoreEncoder, schema);

		MiningModel miningModel = new MiningModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.AVERAGE, treeModels))
			.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));

		return miningModel;
	}

	private List<TreeModel> encodeForest(RGenericVector forest, MiningFunction miningFunction, ScoreEncoder scoreEncoder, Schema schema){
		RNumberVector<?> numTrees = forest.getNumericElement("num.trees");
		RGenericVector childNodeIDs = forest.getGenericElement("child.nodeIDs");
		RGenericVector splitVarIDs = forest.getGenericElement("split.varIDs");
		RGenericVector splitValues = forest.getGenericElement("split.values");
		RGenericVector terminalClassCounts = forest.getGenericElement("terminal.class.counts", false);

		Schema segmentSchema = schema.toAnonymousSchema();

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < ValueUtil.asInt(numTrees.asScalar()); i++){
			TreeModel treeModel = encodeTreeModel(miningFunction, scoreEncoder, childNodeIDs.getGenericValue(i), splitVarIDs.getNumericValue(i), splitValues.getNumericValue(i), (terminalClassCounts != null ? terminalClassCounts.getGenericValue(i) : null), segmentSchema);

			treeModels.add(treeModel);
		}

		return treeModels;
	}

	private TreeModel encodeTreeModel(MiningFunction miningFunction, ScoreEncoder scoreEncoder, RGenericVector childNodeIDs, RNumberVector<?> splitVarIDs, RNumberVector<?> splitValues, RGenericVector terminalClassCounts, Schema schema){
		RNumberVector<?> leftChildIDs = childNodeIDs.getNumericValue(0);
		RNumberVector<?> rightChildIDs = childNodeIDs.getNumericValue(1);

		Node root = encodeNode(True.INSTANCE, 0, scoreEncoder, leftChildIDs, rightChildIDs, splitVarIDs, splitValues, terminalClassCounts, new CategoryManager(), schema);

		TreeModel treeModel = new TreeModel(miningFunction, ModelUtil.createMiningSchema(schema.getLabel()), root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	private Node encodeNode(Predicate predicate, int index, ScoreEncoder scoreEncoder, RNumberVector<?> leftChildIDs, RNumberVector<?> rightChildIDs, RNumberVector<?> splitVarIDs, RNumberVector<?> splitValues, RGenericVector terminalClassCounts, CategoryManager categoryManager, Schema schema){
		int leftIndex = ValueUtil.asInt(leftChildIDs.getValue(index));
		int rightIndex = ValueUtil.asInt(rightChildIDs.getValue(index));

		Number splitValue = splitValues.getValue(index);
		RNumberVector<?> terminalClassCount = (terminalClassCounts != null ? terminalClassCounts.getNumericValue(index) : null);

		if(leftIndex == 0 && rightIndex == 0){
			Node result = new LeafNode(null, predicate);

			return scoreEncoder.encode(result, splitValue, terminalClassCount);
		}

		CategoryManager leftCategoryManager = categoryManager;
		CategoryManager rightCategoryManager = categoryManager;

		Predicate leftPredicate;
		Predicate rightPredicate;

		int splitVarIndex = ValueUtil.asInt(splitVarIDs.getValue(index));

		Feature feature = schema.getFeature(this.hasDependentVar ? (splitVarIndex - 1) : splitVarIndex);

		if(feature instanceof CategoricalFeature){
			CategoricalFeature categoricalFeature = (CategoricalFeature)feature;

			int splitLevelIndex = ValueUtil.asInt(Math.floor(splitValue.doubleValue()));

			FieldName name = categoricalFeature.getName();
			List<?> values = categoricalFeature.getValues();

			java.util.function.Predicate<Object> valueFilter = categoryManager.getValueFilter(name);

			List<Object> leftValues = filterValues(values.subList(0, splitLevelIndex), valueFilter);
			List<Object> rightValues = filterValues(values.subList(splitLevelIndex, values.size()), valueFilter);

			leftCategoryManager = leftCategoryManager.fork(name, leftValues);
			rightCategoryManager = rightCategoryManager.fork(name, rightValues);

			leftPredicate = createPredicate(categoricalFeature, leftValues);
			rightPredicate = createPredicate(categoricalFeature, rightValues);
		} else

		{
			ContinuousFeature continuousFeature = feature.toContinuousFeature();

			leftPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.LESS_OR_EQUAL, splitValue);
			rightPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.GREATER_THAN, splitValue);
		}

		Node leftChild = encodeNode(leftPredicate, leftIndex, scoreEncoder, leftChildIDs, rightChildIDs, splitVarIDs, splitValues, terminalClassCounts, leftCategoryManager, schema);
		Node rightChild = encodeNode(rightPredicate, rightIndex, scoreEncoder, leftChildIDs, rightChildIDs, splitVarIDs, splitValues, terminalClassCounts, rightCategoryManager, schema);

		Node result = new BranchNode(null, predicate)
			.addNodes(leftChild, rightChild);

		return result;
	}

	static
	private List<Object> filterValues(List<?> values, java.util.function.Predicate<Object> valueFilter){
		return values.stream()
			.filter(valueFilter)
			.collect(Collectors.toList());
	}

	static
	private interface ScoreEncoder {

		Node encode(Node node, Number splitValue, RNumberVector<?> terminalClassCount);
	}
}

/*
 * Copyright (c) 2015 Villu Ruusmann
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.DataField;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunctionType;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Node;
import org.dmg.pmml.Output;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.TreeModel;
import org.dmg.pmml.True;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ListFeature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;

public class BinaryTreeConverter extends ModelConverter<S4Object> {

	private MiningFunctionType miningFunction = null;

	private Map<FieldName, Integer> featureIndexes = new LinkedHashMap<>();


	@Override
	public void encodeFeatures(S4Object binaryTree, FeatureMapper featureMapper){
		S4Object responses = (S4Object)binaryTree.getAttributeValue("responses");
		RGenericVector tree = (RGenericVector)binaryTree.getAttributeValue("tree");

		encodeResponse(responses, featureMapper);
		encodeVariableList(tree, featureMapper);
	}

	@Override
	public Schema createSchema(FeatureMapper featureMapper){
		return featureMapper.createSupervisedSchema();
	}

	@Override
	public TreeModel encodeModel(S4Object binaryTree, Schema schema){
		RGenericVector tree = (RGenericVector)binaryTree.getAttributeValue("tree");

		Output output = encodeOutput(schema);

		TreeModel treeModel = encodeTreeModel(tree, schema)
			.setOutput(output);

		return treeModel;
	}

	private void encodeResponse(S4Object responses, FeatureMapper featureMapper){
		RGenericVector variables = (RGenericVector)responses.getAttributeValue("variables");
		RBooleanVector is_nominal = (RBooleanVector)responses.getAttributeValue("is_nominal");
		RGenericVector levels = (RGenericVector)responses.getAttributeValue("levels");

		RStringVector names = variables.names();

		String name = names.asScalar();

		Boolean categorical = is_nominal.getValue(name);

		if((Boolean.TRUE).equals(categorical)){
			this.miningFunction = MiningFunctionType.CLASSIFICATION;

			RExp targetVariable = variables.getValue(name);

			RStringVector targetVariableClass = (RStringVector)targetVariable.getAttributeValue("class");

			RStringVector targetCategories = (RStringVector)levels.getValue(name);

			featureMapper.append(FieldName.create(name), RExpUtil.getDataType(targetVariableClass.asScalar()), targetCategories.getValues());
		} else

		if((Boolean.FALSE).equals(categorical)){
			this.miningFunction = MiningFunctionType.REGRESSION;

			featureMapper.append(FieldName.create(name), false);
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	private void encodeVariableList(RGenericVector tree, FeatureMapper featureMapper){
		RBooleanVector terminal = (RBooleanVector)tree.getValue("terminal");
		RGenericVector psplit = (RGenericVector)tree.getValue("psplit");
		RGenericVector left = (RGenericVector)tree.getValue("left");
		RGenericVector right = (RGenericVector)tree.getValue("right");

		if((Boolean.TRUE).equals(terminal.asScalar())){
			return;
		}

		RNumberVector<?> splitpoint = (RNumberVector<?>)psplit.getValue("splitpoint");
		RStringVector variableName = (RStringVector)psplit.getValue("variableName");

		FieldName name = FieldName.create(variableName.asScalar());

		DataField dataField = featureMapper.getDataField(name);
		if(dataField == null){

			if(splitpoint instanceof RDoubleVector){
				featureMapper.append(name, false);
			} else

			if(splitpoint instanceof RIntegerVector){
				RStringVector levels = (RStringVector)splitpoint.getAttributeValue("levels");

				featureMapper.append(name, levels.getValues());
			} else

			{
				throw new IllegalArgumentException();
			}

			this.featureIndexes.put(name, this.featureIndexes.size());
		}

		encodeVariableList(left, featureMapper);
		encodeVariableList(right, featureMapper);
	}

	private TreeModel encodeTreeModel(RGenericVector tree, Schema schema){
		Node root = new Node()
			.setPredicate(new True());

		encodeNode(root, tree, schema);

		MiningSchema miningSchema = ModelUtil.createMiningSchema(schema, root);

		TreeModel treeModel = new TreeModel(this.miningFunction, miningSchema, root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	private void encodeNode(Node node, RGenericVector tree, Schema schema){
		RIntegerVector nodeId = (RIntegerVector)tree.getValue("nodeID");
		RBooleanVector terminal = (RBooleanVector)tree.getValue("terminal");
		RGenericVector psplit = (RGenericVector)tree.getValue("psplit");
		RGenericVector ssplits = (RGenericVector)tree.getValue("ssplits");
		RDoubleVector prediction = (RDoubleVector)tree.getValue("prediction");
		RGenericVector left = (RGenericVector)tree.getValue("left");
		RGenericVector right = (RGenericVector)tree.getValue("right");

		node.setId(String.valueOf(nodeId.asScalar()));

		if((Boolean.TRUE).equals(terminal.asScalar())){
			node = encodeScore(node, prediction, schema);

			return;
		}

		List<Predicate> predicates = encodeSplit(psplit, ssplits, schema);

		Node leftChild = new Node()
			.setPredicate(predicates.get(0));

		encodeNode(leftChild, left, schema);

		Node rightChild = new Node()
			.setPredicate(predicates.get(1));

		encodeNode(rightChild, right, schema);

		node.addNodes(leftChild, rightChild);
	}

	private List<Predicate> encodeSplit(RGenericVector psplit, RGenericVector ssplits, Schema schema){
		RNumberVector<?> splitpoint = (RNumberVector<?>)psplit.getValue("splitpoint");
		RStringVector variableName = (RStringVector)psplit.getValue("variableName");

		if(ssplits.size() > 0){
			throw new IllegalArgumentException();
		}

		FieldName name = FieldName.create(variableName.asScalar());

		Integer index = this.featureIndexes.get(name);
		if(index == null){
			throw new IllegalArgumentException();
		}

		Feature feature = schema.getFeature(index);

		if(feature instanceof ListFeature){
			return encodeCategoricalSplit((ListFeature)feature, (List<Integer>)splitpoint.getValues());
		} else

		if(feature instanceof ContinuousFeature){
			return encodeContinuousSplit(feature, (Double)splitpoint.asScalar());
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	private Node encodeScore(Node node, RDoubleVector probabilities, Schema schema){

		switch(this.miningFunction){
			case REGRESSION:
				return encodeRegressionScore(node, probabilities);
			case CLASSIFICATION:
				return encodeClassificationScore(node, probabilities, schema);
			default:
				throw new IllegalArgumentException();
		}
	}

	private Output encodeOutput(Schema schema){

		switch(this.miningFunction){
			case REGRESSION:
				return encodeRegressionOutput();
			case CLASSIFICATION:
				return encodeClassificationOutput(schema);
			default:
				return null;
		}
	}

	static
	private List<Predicate> encodeContinuousSplit(Feature feature, Double split){
		String value = ValueUtil.formatValue(split);

		Predicate leftPredicate = new SimplePredicate()
			.setField(feature.getName())
			.setOperator(SimplePredicate.Operator.LESS_OR_EQUAL)
			.setValue(value);

		Predicate rightPredicate = new SimplePredicate()
			.setField(feature.getName())
			.setOperator(SimplePredicate.Operator.GREATER_THAN)
			.setValue(value);

		return Arrays.asList(leftPredicate, rightPredicate);
	}

	static
	private List<Predicate> encodeCategoricalSplit(ListFeature listFeature, List<Integer> splits){
		List<String> values = listFeature.getValues();

		if(splits.size() != values.size()){
			throw new IllegalArgumentException();
		}

		List<String> leftValues = new ArrayList<>();
		List<String> rightValues = new ArrayList<>();

		for(int i = 0; i < splits.size(); i++){
			Integer split = splits.get(i);
			String value = listFeature.getValue(i);

			if(split == 1){
				leftValues.add(value);
			} else

			{
				rightValues.add(value);
			}
		}

		Predicate leftPredicate = new SimpleSetPredicate()
			.setField(listFeature.getName())
			.setBooleanOperator(SimpleSetPredicate.BooleanOperator.IS_IN)
			.setArray(FeatureUtil.createArray(listFeature, leftValues));

		Predicate rightPredicate = new SimpleSetPredicate()
			.setField(listFeature.getName())
			.setBooleanOperator(SimpleSetPredicate.BooleanOperator.IS_IN)
			.setArray(FeatureUtil.createArray(listFeature, rightValues));

		return Arrays.asList(leftPredicate, rightPredicate);
	}

	static
	private Node encodeRegressionScore(Node node, RDoubleVector probabilities){

		if(probabilities.size() != 1){
			throw new IllegalArgumentException();
		}

		Double probability = probabilities.asScalar();

		node.setScore(ValueUtil.formatValue(probability));

		return node;
	}

	static
	private Node encodeClassificationScore(Node node, RDoubleVector probabilities, Schema schema){
		List<String> targetCategories = schema.getTargetCategories();

		if(probabilities.size() != targetCategories.size()){
			throw new IllegalArgumentException();
		}

		List<ScoreDistribution> scoreDistributions = node.getScoreDistributions();

		Double maxProbability = null;

		for(int i = 0; i < targetCategories.size(); i++){
			String targetCategory = targetCategories.get(i);

			Double probability = probabilities.getValue(i);

			if(maxProbability == null || maxProbability.compareTo(probability) < 0){
				node.setScore(targetCategory);

				maxProbability = probability;
			}

			ScoreDistribution scoreDistribution = new ScoreDistribution(targetCategory, probability);

			scoreDistributions.add(scoreDistribution);
		}

		return node;
	}

	static
	private Output encodeRegressionOutput(){
		Output output = new Output()
			.addOutputFields(ModelUtil.createEntityIdField(FieldName.create("nodeId")));

		return output;
	}

	static
	private Output encodeClassificationOutput(Schema schema){
		List<String> targetCategories = schema.getTargetCategories();

		Output output = new Output(ModelUtil.createProbabilityFields(targetCategories))
			.addOutputFields(ModelUtil.createEntityIdField(FieldName.create("nodeId")));

		return output;
	}
}
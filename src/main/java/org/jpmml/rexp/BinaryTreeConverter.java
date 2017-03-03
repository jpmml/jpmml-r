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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.tree.TreeModelUtil;

public class BinaryTreeConverter extends TreeModelConverter<S4Object> {

	private MiningFunction miningFunction = null;

	private Map<FieldName, Integer> featureIndexes = new LinkedHashMap<>();


	public BinaryTreeConverter(S4Object binaryTree){
		super(binaryTree);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		S4Object binaryTree = getObject();

		S4Object responses = (S4Object)binaryTree.getAttributeValue("responses");
		RGenericVector tree = (RGenericVector)binaryTree.getAttributeValue("tree");

		encodeResponse(responses, encoder);
		encodeVariableList(tree, encoder);
	}

	@Override
	public TreeModel encodeModel(Schema schema){
		S4Object binaryTree = getObject();

		RGenericVector tree = (RGenericVector)binaryTree.getAttributeValue("tree");

		TreeModel treeModel = encodeTreeModel(tree, schema)
			.setOutput(TreeModelUtil.createNodeOutput(schema));

		return treeModel;
	}

	private void encodeResponse(S4Object responses, RExpEncoder encoder){
		RGenericVector variables = (RGenericVector)responses.getAttributeValue("variables");
		RBooleanVector is_nominal = (RBooleanVector)responses.getAttributeValue("is_nominal");
		RGenericVector levels = (RGenericVector)responses.getAttributeValue("levels");

		RStringVector variableNames = variables.names();

		String variableName = variableNames.asScalar();

		DataField dataField;

		Boolean categorical = is_nominal.getValue(variableName);
		if((Boolean.TRUE).equals(categorical)){
			this.miningFunction = MiningFunction.CLASSIFICATION;

			RExp targetVariable = variables.getValue(variableName);

			RStringVector targetVariableClass = (RStringVector)targetVariable.getAttributeValue("class");

			RStringVector targetCategories = (RStringVector)levels.getValue(variableName);

			dataField = encoder.createDataField(FieldName.create(variableName), OpType.CATEGORICAL, RExpUtil.getDataType(targetVariableClass.asScalar()), targetCategories.getValues());
		} else

		if((Boolean.FALSE).equals(categorical)){
			this.miningFunction = MiningFunction.REGRESSION;

			dataField = encoder.createDataField(FieldName.create(variableName), OpType.CONTINUOUS, DataType.DOUBLE);
		} else

		{
			throw new IllegalArgumentException();
		}

		encoder.setLabel(dataField);
	}

	private void encodeVariableList(RGenericVector tree, RExpEncoder encoder){
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

		DataField dataField = encoder.getDataField(name);
		if(dataField == null){

			if(splitpoint instanceof RIntegerVector){
				RStringVector levels = (RStringVector)splitpoint.getAttributeValue("levels");

				dataField = encoder.createDataField(name, OpType.CATEGORICAL, null, levels.getValues());
			} else


			if(splitpoint instanceof RDoubleVector){
				dataField = encoder.createDataField(name, OpType.CONTINUOUS, DataType.DOUBLE);
			} else

			{
				throw new IllegalArgumentException();
			}

			encoder.addFeature(dataField);

			this.featureIndexes.put(name, this.featureIndexes.size());
		}

		encodeVariableList(left, encoder);
		encodeVariableList(right, encoder);
	}

	private TreeModel encodeTreeModel(RGenericVector tree, Schema schema){
		Node root = new Node()
			.setPredicate(new True());

		encodeNode(root, tree, schema);

		TreeModel treeModel = new TreeModel(this.miningFunction, ModelUtil.createMiningSchema(schema), root)
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

		RNumberVector<?> splitpoint = (RNumberVector<?>)psplit.getValue("splitpoint");
		RStringVector variableName = (RStringVector)psplit.getValue("variableName");

		if(ssplits.size() > 0){
			throw new IllegalArgumentException();
		}

		Predicate leftPredicate;
		Predicate rightPredicate;

		FieldName name = FieldName.create(variableName.asScalar());

		Integer index = this.featureIndexes.get(name);
		if(index == null){
			throw new IllegalArgumentException();
		}

		Feature feature = schema.getFeature(index);

		if(feature instanceof CategoricalFeature){
			CategoricalFeature categoricalFeature = (CategoricalFeature)feature;

			List<String> values = categoricalFeature.getValues();

			leftPredicate = createSimpleSetPredicate(categoricalFeature, selectValues(values, (List<Integer>)splitpoint.getValues(), true));
			rightPredicate = createSimpleSetPredicate(categoricalFeature, selectValues(values, (List<Integer>)splitpoint.getValues(), false));
		} else

		{
			ContinuousFeature continuousFeature = feature.toContinuousFeature();

			String value = ValueUtil.formatValue((Double)splitpoint.asScalar());

			leftPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.LESS_OR_EQUAL, value);
			rightPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.GREATER_THAN, value);
		}

		Node leftChild = new Node()
			.setPredicate(leftPredicate);

		encodeNode(leftChild, left, schema);

		Node rightChild = new Node()
			.setPredicate(rightPredicate);

		encodeNode(rightChild, right, schema);

		node.addNodes(leftChild, rightChild);
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

	static
	private <E> List<E> selectValues(List<E> values, List<Integer> splits, boolean left){

		if(values.size() != splits.size()){
			throw new IllegalArgumentException();
		}

		List<E> result = new ArrayList<>();

		for(int i = 0; i < values.size(); i++){
			E value = values.get(i);
			Integer split = splits.get(i);

			boolean append;

			if(left){
				append = (split == 1);
			} else

			{
				append = (split == 0);
			} // End if

			if(append){
				result.add(value);
			}
		}

		return result;
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
		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		if(categoricalLabel.size() != probabilities.size()){
			throw new IllegalArgumentException();
		}

		List<ScoreDistribution> scoreDistributions = node.getScoreDistributions();

		Double maxProbability = null;

		for(int i = 0; i < categoricalLabel.size(); i++){
			String value = categoricalLabel.getValue(i);
			Double probability = probabilities.getValue(i);

			if(maxProbability == null || (maxProbability).compareTo(probability) < 0){
				node.setScore(value);

				maxProbability = probability;
			}

			ScoreDistribution scoreDistribution = new ScoreDistribution(value, probability);

			scoreDistributions.add(scoreDistribution);
		}

		return node;
	}
}
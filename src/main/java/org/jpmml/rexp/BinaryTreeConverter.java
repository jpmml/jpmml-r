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
import org.dmg.pmml.Output;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.BranchNode;
import org.dmg.pmml.tree.ClassifierNode;
import org.dmg.pmml.tree.LeafNode;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.SchemaUtil;

public class BinaryTreeConverter extends TreeModelConverter<S4Object> {

	private MiningFunction miningFunction = null;

	private Map<FieldName, Integer> featureIndexes = new LinkedHashMap<>();


	public BinaryTreeConverter(S4Object binaryTree){
		super(binaryTree);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		S4Object binaryTree = getObject();

		S4Object responses = (S4Object)binaryTree.getAttribute("responses");
		RGenericVector tree = binaryTree.getGenericAttribute("tree");

		encodeResponse(responses, encoder);
		encodeVariableList(tree, encoder);
	}

	@Override
	public TreeModel encodeModel(Schema schema){
		S4Object binaryTree = getObject();

		RGenericVector tree = binaryTree.getGenericAttribute("tree");

		Output output;

		switch(this.miningFunction){
			case REGRESSION:
				output = new Output();
				break;
			case CLASSIFICATION:
				CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

				output = ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel);
				break;
			default:
				throw new IllegalArgumentException();
		}

		output.addOutputFields(ModelUtil.createEntityIdField(FieldName.create("nodeId")));

		TreeModel treeModel = encodeTreeModel(tree, schema)
			.setOutput(output);

		return treeModel;
	}

	private void encodeResponse(S4Object responses, RExpEncoder encoder){
		RGenericVector variables = responses.getGenericAttribute("variables");
		RBooleanVector is_nominal = responses.getBooleanAttribute("is_nominal");
		RGenericVector levels = responses.getGenericAttribute("levels");

		RStringVector variableNames = variables.names();

		String variableName = variableNames.asScalar();

		DataField dataField;

		Boolean categorical = is_nominal.getElement(variableName);
		if((Boolean.TRUE).equals(categorical)){
			this.miningFunction = MiningFunction.CLASSIFICATION;

			RExp targetVariable = variables.getElement(variableName);

			RStringVector targetVariableClass = RExpUtil.getClassNames(targetVariable);

			RStringVector targetCategories = levels.getStringElement(variableName);

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
		RBooleanVector terminal = tree.getBooleanElement("terminal");
		RGenericVector psplit = tree.getGenericElement("psplit");
		RGenericVector left = tree.getGenericElement("left");
		RGenericVector right = tree.getGenericElement("right");

		if((Boolean.TRUE).equals(terminal.asScalar())){
			return;
		}

		RNumberVector<?> splitpoint = psplit.getNumericElement("splitpoint");
		RStringVector variableName = psplit.getStringElement("variableName");

		FieldName name = FieldName.create(variableName.asScalar());

		DataField dataField = encoder.getDataField(name);
		if(dataField == null){

			if(splitpoint instanceof RIntegerVector){
				RStringVector levels = splitpoint.getStringAttribute("levels");

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
		Node root = encodeNode(tree, True.INSTANCE, schema);

		TreeModel treeModel = new TreeModel(this.miningFunction, ModelUtil.createMiningSchema(schema.getLabel()), root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	private Node encodeNode(RGenericVector tree, Predicate predicate, Schema schema){
		RIntegerVector nodeId = tree.getIntegerElement("nodeID");
		RBooleanVector terminal = tree.getBooleanElement("terminal");
		RGenericVector psplit = tree.getGenericElement("psplit");
		RGenericVector ssplits = tree.getGenericElement("ssplits");
		RDoubleVector prediction = tree.getDoubleElement("prediction");
		RGenericVector left = tree.getGenericElement("left");
		RGenericVector right = tree.getGenericElement("right");

		Integer id = nodeId.asScalar();

		if((Boolean.TRUE).equals(terminal.asScalar())){
			Node result = new LeafNode(null, predicate)
				.setId(id);

			return encodeScore(result, prediction, schema);
		}

		RNumberVector<?> splitpoint = psplit.getNumericElement("splitpoint");
		RStringVector variableName = psplit.getStringElement("variableName");

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

			List<?> values = categoricalFeature.getValues();
			List<Integer> splitValues = (List<Integer>)splitpoint.getValues();

			leftPredicate = createPredicate(categoricalFeature, selectValues(values, splitValues, true));
			rightPredicate = createPredicate(categoricalFeature, selectValues(values, splitValues, false));
		} else

		{
			ContinuousFeature continuousFeature = feature.toContinuousFeature();

			Number value = splitpoint.asScalar();

			leftPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.LESS_OR_EQUAL, value);
			rightPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.GREATER_THAN, value);
		}

		Node leftChild = encodeNode(left, leftPredicate, schema);
		Node rightChild = encodeNode(right, rightPredicate, schema);

		Node result = new BranchNode(null, predicate)
			.setId(id)
			.addNodes(leftChild, rightChild);

		return result;
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

		node.setScore(probability);

		return node;
	}

	static
	private Node encodeClassificationScore(Node node, RDoubleVector probabilities, Schema schema){
		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		SchemaUtil.checkSize(probabilities.size(), categoricalLabel);

		node = new ClassifierNode(node);

		List<ScoreDistribution> scoreDistributions = node.getScoreDistributions();

		Double maxProbability = null;

		for(int i = 0; i < categoricalLabel.size(); i++){
			Object value = categoricalLabel.getValue(i);
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
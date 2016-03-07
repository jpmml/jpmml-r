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
import java.util.Collections;
import java.util.List;

import org.dmg.pmml.DataDictionary;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunctionType;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Node;
import org.dmg.pmml.Output;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.TreeModel;
import org.dmg.pmml.True;
import org.dmg.pmml.Value;
import org.jpmml.converter.FieldComparator;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.ValueUtil;

public class BinaryTreeConverter extends Converter {

	private MiningFunctionType miningFunction = null;

	private List<DataField> dataFields = new ArrayList<>();


	@Override
	public PMML convert(RExp rexp){
		return convert((S4Object)rexp);
	}

	private PMML convert(S4Object binaryTree){
		S4Object responses = (S4Object)binaryTree.getAttributeValue("responses");
		RGenericVector tree = (RGenericVector)binaryTree.getAttributeValue("tree");

		initTargetField(responses);

		Output output = encodeOutput();

		TreeModel treeModel = encodeTreeModel(tree)
			.setOutput(output);

		Collections.sort(this.dataFields.subList(1, this.dataFields.size()), new FieldComparator<>());

		DataDictionary dataDictionary = new DataDictionary(this.dataFields);

		PMML pmml = new PMML("4.2", createHeader(), dataDictionary)
			.addModels(treeModel);

		return pmml;
	}

	private void initTargetField(S4Object responses){
		RGenericVector variables = (RGenericVector)responses.getAttributeValue("variables");
		RBooleanVector is_nominal = (RBooleanVector)responses.getAttributeValue("is_nominal");
		RGenericVector levels = (RGenericVector)responses.getAttributeValue("levels");

		RStringVector names = variables.names();

		String name = names.asScalar();

		DataField dataField;

		Boolean categorical = is_nominal.getValue(name);

		if((Boolean.TRUE).equals(categorical)){
			this.miningFunction = MiningFunctionType.CLASSIFICATION;

			RExp target = variables.getValue(name);

			RStringVector targetClass = (RStringVector)target.getAttributeValue("class");

			dataField = PMMLUtil.createDataField(FieldName.create(name), RExpUtil.getDataType(targetClass.asScalar()));

			RStringVector targetLevels = (RStringVector)levels.getValue(name);

			List<Value> values = dataField.getValues();
			values.addAll(PMMLUtil.createValues(targetLevels.getValues()));
		} else

		if((Boolean.FALSE).equals(categorical)){
			this.miningFunction = MiningFunctionType.REGRESSION;

			dataField = PMMLUtil.createDataField(FieldName.create(name), false);
		} else

		{
			throw new IllegalArgumentException();
		}

		this.dataFields.add(dataField);
	}

	private DataField findDataField(FieldName name){
		return PMMLUtil.getField(name, this.dataFields, 1);
	}

	private DataField createDataField(FieldName name, DataType dataType){
		DataField dataField = PMMLUtil.createDataField(name, dataType);

		this.dataFields.add(dataField);

		return dataField;
	}

	private TreeModel encodeTreeModel(RGenericVector tree){
		Node root = new Node()
			.setPredicate(new True());

		encodeNode(root, tree);

		MiningSchema miningSchema = ModelUtil.createMiningSchema(this.dataFields, root);

		TreeModel treeModel = new TreeModel(this.miningFunction, miningSchema, root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	private void encodeNode(Node node, RGenericVector tree){
		RIntegerVector nodeId = (RIntegerVector)tree.getValue("nodeID");
		RBooleanVector terminal = (RBooleanVector)tree.getValue("terminal");
		RGenericVector psplit = (RGenericVector)tree.getValue("psplit");
		RGenericVector ssplits = (RGenericVector)tree.getValue("ssplits");
		RDoubleVector prediction = (RDoubleVector)tree.getValue("prediction");
		RGenericVector left = (RGenericVector)tree.getValue("left");
		RGenericVector right = (RGenericVector)tree.getValue("right");

		node.setId(String.valueOf(nodeId.asScalar()));

		if((Boolean.TRUE).equals(terminal.asScalar())){
			node = encodeScore(node, prediction);

			return;
		}

		List<Predicate> predicates = encodeSplit(psplit, ssplits);

		Node leftChild = new Node()
			.setPredicate(predicates.get(0));

		encodeNode(leftChild, left);

		Node rightChild = new Node()
			.setPredicate(predicates.get(1));

		encodeNode(rightChild, right);

		node.addNodes(leftChild, rightChild);
	}

	private List<Predicate> encodeSplit(RGenericVector psplit, RGenericVector ssplits){
		RNumberVector<?> splitpoint = (RNumberVector<?>)psplit.getValue("splitpoint");
		RStringVector variableName = (RStringVector)psplit.getValue("variableName");

		if(ssplits.size() > 0){
			throw new IllegalArgumentException();
		}

		FieldName name = FieldName.create(variableName.asScalar());

		if(splitpoint instanceof RDoubleVector){
			DataField dataField;

			try {
				dataField = findDataField(name);
			} catch(IllegalArgumentException iae){
				dataField = createDataField(name, DataType.DOUBLE);
			}

			return encodeContinuousSplit(dataField, (Double)splitpoint.asScalar());
		} else

		if(splitpoint instanceof RIntegerVector){
			RStringVector levels = (RStringVector)splitpoint.getAttributeValue("levels");

			List<Value> levelValues = PMMLUtil.createValues(levels.getValues());

			DataField dataField;

			try {
				dataField = findDataField(name);
			} catch(IllegalArgumentException iae){
				dataField = createDataField(name, DataType.STRING);

				List<Value> values = dataField.getValues();
				values.addAll(levelValues);
			}

			return encodeCategoricalSplit(dataField, (List<Integer>)splitpoint.getValues(), levelValues);
		}

		throw new IllegalArgumentException();
	}

	private List<Predicate> encodeContinuousSplit(DataField dataField, Double split){
		String value = ValueUtil.formatValue(split);

		Predicate leftPredicate = new SimplePredicate()
			.setField(dataField.getName())
			.setOperator(SimplePredicate.Operator.LESS_OR_EQUAL)
			.setValue(value);

		Predicate rightPredicate = new SimplePredicate()
			.setField(dataField.getName())
			.setOperator(SimplePredicate.Operator.GREATER_THAN)
			.setValue(value);

		return Arrays.asList(leftPredicate, rightPredicate);
	}

	private List<Predicate> encodeCategoricalSplit(DataField dataField, List<Integer> splits, List<Value> values){
		List<Value> leftValues = new ArrayList<>();
		List<Value> rightValues = new ArrayList<>();

		if(splits.size() != values.size()){
			throw new IllegalArgumentException();
		}

		for(int i = 0; i < splits.size(); i++){
			Integer split = splits.get(i);
			Value value = values.get(i);

			if(split == 1){
				leftValues.add(value);
			} else

			{
				rightValues.add(value);
			}
		}

		Predicate leftPredicate = new SimpleSetPredicate()
			.setField(dataField.getName())
			.setBooleanOperator(SimpleSetPredicate.BooleanOperator.IS_IN)
			.setArray(PMMLUtil.createArray(dataField.getDataType(), leftValues));

		Predicate rightPredicate = new SimpleSetPredicate()
			.setField(dataField.getName())
			.setBooleanOperator(SimpleSetPredicate.BooleanOperator.IS_IN)
			.setArray(PMMLUtil.createArray(dataField.getDataType(), rightValues));

		return Arrays.asList(leftPredicate, rightPredicate);
	}

	private Node encodeScore(Node node, RDoubleVector probabilities){

		switch(this.miningFunction){
			case CLASSIFICATION:
				return encodeClassificationScore(node, probabilities);
			case REGRESSION:
				return encodeRegressionScore(node, probabilities);
			default:
				throw new IllegalArgumentException();
		}
	}

	private Node encodeClassificationScore(Node node, RDoubleVector probabilities){
		DataField dataField = this.dataFields.get(0);

		List<Value> values = dataField.getValues();

		if(probabilities.size() != values.size()){
			throw new IllegalArgumentException();
		}

		List<ScoreDistribution> scoreDistributions = node.getScoreDistributions();

		Double maxProbability = null;

		for(int i = 0; i < values.size(); i++){
			Value value = values.get(i);

			Double probability = probabilities.getValue(i);

			if(maxProbability == null || maxProbability.compareTo(probability) < 0){
				node.setScore(value.getValue());

				maxProbability = probability;
			}

			ScoreDistribution scoreDistribution = new ScoreDistribution(value.getValue(), probability);

			scoreDistributions.add(scoreDistribution);
		}

		return node;
	}

	private Node encodeRegressionScore(Node node, RDoubleVector probabilities){

		if(probabilities.size() != 1){
			throw new IllegalArgumentException();
		}

		Double probability = probabilities.asScalar();

		node.setScore(ValueUtil.formatValue(probability));

		return node;
	}

	private Output encodeOutput(){

		switch(this.miningFunction){
			case REGRESSION:
				return encodeRegressionOutput();
			case CLASSIFICATION:
				return encodeClassificationOutput();
			default:
				return null;
		}
	}

	private Output encodeRegressionOutput(){
		Output output = new Output()
			.addOutputFields(ModelUtil.createEntityIdField(FieldName.create("nodeId")));

		return output;
	}

	private Output encodeClassificationOutput(){
		DataField dataField = this.dataFields.get(0);

		Output output = new Output(ModelUtil.createProbabilityFields(dataField))
			.addOutputFields(ModelUtil.createEntityIdField(FieldName.create("nodeId")));

		return output;
	}
}
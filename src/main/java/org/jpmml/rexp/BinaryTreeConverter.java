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
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.ValueUtil;
import org.jpmml.model.visitors.FieldReferenceFinder;

public class BinaryTreeConverter extends Converter {

	private MiningFunctionType miningFunction = null;

	private List<DataField> dataFields = new ArrayList<>();


	@Override
	public PMML convert(RExp binaryTree){
		RExp responses = RExpUtil.field(binaryTree, "responses");
		RExp tree = RExpUtil.field(binaryTree, "tree");

		initTargetField(responses);

		Output output = encodeOutput();

		TreeModel treeModel = encodeTreeModel(tree)
			.setOutput(output);

		Collections.sort(this.dataFields.subList(1, this.dataFields.size()), new FieldComparator<>());

		DataDictionary dataDictionary = new DataDictionary(this.dataFields);

		PMML pmml = new PMML("4.2", PMMLUtil.createHeader(Converter.NAME), dataDictionary)
			.addModels(treeModel);

		return pmml;
	}

	private void initTargetField(RExp responses){
		RExp variables = RExpUtil.field(responses, "variables");
		RExp is_nominal = RExpUtil.field(responses, "is_nominal");
		RExp levels = RExpUtil.field(responses, "levels");

		RExp names = RExpUtil.attribute(variables, "names");
		if(names.getStringValueCount() > 1){
			throw new IllegalArgumentException();
		}

		RString name = names.getStringValue(0);

		DataField dataField;

		RBoolean categorical = RExpUtil.booleanField(is_nominal, name.getStrval());

		if((RBoolean.T).equals(categorical)){
			this.miningFunction = MiningFunctionType.CLASSIFICATION;

			RExp target = RExpUtil.field(variables, name.getStrval());

			RExp targetClass = RExpUtil.attribute(target, "class");

			RString targetClassName = targetClass.getStringValue(0);

			dataField = PMMLUtil.createDataField(FieldName.create(name.getStrval()), RExpUtil.getDataType(targetClassName.getStrval()));

			RExp targetLevels = RExpUtil.field(levels, name.getStrval());

			List<Value> values = dataField.getValues();
			values.addAll(PMMLUtil.createValues(RExpUtil.getStringList(targetLevels)));
		} else

		if((RBoolean.F).equals(categorical)){
			this.miningFunction = MiningFunctionType.REGRESSION;

			dataField = PMMLUtil.createDataField(FieldName.create(name.getStrval()), false);
		} else

		{
			throw new IllegalArgumentException();
		}

		this.dataFields.add(dataField);
	}

	private DataField getDataField(FieldName name){

		for(int i = 1; i < this.dataFields.size(); i++){
			DataField dataField = this.dataFields.get(i);

			if((dataField.getName()).equals(name)){
				return dataField;
			}
		}

		return null;
	}

	private DataField createDataField(FieldName name, DataType dataType){
		DataField dataField = new DataField()
			.setName(name)
			.setDataType(dataType);

		dataField = PMMLUtil.refineDataField(dataField, dataType);

		this.dataFields.add(dataField);

		return dataField;
	}

	private TreeModel encodeTreeModel(RExp tree){
		Node root = new Node()
			.setPredicate(new True());

		encodeNode(root, tree);

		DataField dataField = this.dataFields.get(0);

		FieldReferenceFinder fieldReferenceFinder = new FieldReferenceFinder();
		fieldReferenceFinder.applyTo(root);

		MiningSchema miningSchema = PMMLUtil.createMiningSchema(dataField, fieldReferenceFinder);

		TreeModel treeModel = new TreeModel(this.miningFunction, miningSchema, root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	private void encodeNode(Node node, RExp tree){
		RExp nodeId = RExpUtil.field(tree, "nodeID");
		RExp terminal = RExpUtil.field(tree, "terminal");
		RExp psplit = RExpUtil.field(tree, "psplit");
		RExp ssplits = RExpUtil.field(tree, "ssplits");
		RExp prediction = RExpUtil.field(tree, "prediction");
		RExp left = RExpUtil.field(tree, "left");
		RExp right = RExpUtil.field(tree, "right");

		node.setId(String.valueOf(nodeId.getIntValue(0)));

		if((RBoolean.T).equals(terminal.getBooleanValue(0))){
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

	private List<Predicate> encodeSplit(RExp split, RExp ssplits){
		RExp splitpoint = RExpUtil.field(split, "splitpoint");
		RExp variableName = RExpUtil.field(split, "variableName");

		if(ssplits.getRexpValueCount() > 0){
			throw new IllegalArgumentException();
		}

		RString name = variableName.getStringValue(0);

		FieldName field = FieldName.create(name.getStrval());

		if(splitpoint.getRealValueCount() == 1){
			DataField dataField = getDataField(field);
			if(dataField == null){
				dataField = createDataField(field, DataType.DOUBLE);
			}

			return encodeContinuousSplit(dataField, splitpoint.getRealValue(0));
		} // End if

		if(splitpoint.getIntValueCount() > 0){
			RExp levels = RExpUtil.attribute(splitpoint, "levels");

			List<Value> levelValues = PMMLUtil.createValues(RExpUtil.getStringList(levels));

			DataField dataField = getDataField(field);
			if(dataField == null){
				dataField = createDataField(field, DataType.STRING);

				List<Value> values = dataField.getValues();
				values.addAll(levelValues);
			}

			return encodeCategoricalSplit(dataField, splitpoint.getIntValueList(), levelValues);
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

	private Node encodeScore(Node node, RExp probabilities){

		switch(this.miningFunction){
			case CLASSIFICATION:
				return encodeClassificationScore(node, probabilities);
			case REGRESSION:
				return encodeRegressionScore(node, probabilities);
			default:
				throw new IllegalArgumentException();
		}
	}

	private Node encodeClassificationScore(Node node, RExp probabilities){
		DataField dataField = this.dataFields.get(0);

		List<Value> values = dataField.getValues();

		if(probabilities.getRealValueCount() != values.size()){
			throw new IllegalArgumentException();
		}

		List<ScoreDistribution> scoreDistributions = node.getScoreDistributions();

		Double maxProbability = null;

		for(int i = 0; i < values.size(); i++){
			Value value = values.get(i);

			Double probability = probabilities.getRealValue(i);

			if(maxProbability == null || maxProbability.compareTo(probability) < 0){
				node.setScore(value.getValue());

				maxProbability = probability;
			}

			ScoreDistribution scoreDistribution = new ScoreDistribution(value.getValue(), probability);

			scoreDistributions.add(scoreDistribution);
		}

		return node;
	}

	private Node encodeRegressionScore(Node node, RExp probabilities){

		if(probabilities.getRealValueCount() != 1){
			throw new IllegalArgumentException();
		}

		Double probability = probabilities.getRealValue(0);

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
			.addOutputFields(PMMLUtil.createEntityIdField(FieldName.create("nodeId")));

		return output;
	}

	private Output encodeClassificationOutput(){
		DataField dataField = this.dataFields.get(0);

		Output output = new Output(PMMLUtil.createProbabilityFields(dataField))
			.addOutputFields(PMMLUtil.createEntityIdField(FieldName.create("nodeId")));

		return output;
	}
}
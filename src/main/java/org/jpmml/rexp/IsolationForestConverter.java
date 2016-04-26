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

import org.dmg.pmml.DataDictionary;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FeatureType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunctionType;
import org.dmg.pmml.MiningModel;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.MultipleModelMethodType;
import org.dmg.pmml.Node;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.Segmentation;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.TreeModel;
import org.dmg.pmml.True;
import org.jpmml.converter.MiningModelUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.ValueUtil;

public class IsolationForestConverter extends Converter {

	private List<DataField> dataFields = new ArrayList<>();


	@Override
	public PMML convert(RExp rexp){
		return convert((RGenericVector)rexp);
	}

	private PMML convert(RGenericVector iForest){
		RStringVector xcols = (RStringVector)iForest.getValue("xcols");
		RGenericVector trees = (RGenericVector)iForest.getValue("trees");
		RDoubleVector ntree = (RDoubleVector)iForest.getValue("ntree");
		RBooleanVector colisfactor = (RBooleanVector)iForest.getValue("colisfactor");

		if(trees == null){
			throw new IllegalArgumentException();
		}

		RIntegerVector xrow = (RIntegerVector)trees.getValue("xrow");

		if(xcols.size() != colisfactor.size()){
			throw new IllegalArgumentException();
		}

		boolean hasFactors = false;

		for(int i = 0; i < colisfactor.size(); i++){
			hasFactors |= colisfactor.getValue(i);
		}

		if(hasFactors){
			throw new IllegalArgumentException();
		}

		initFields(xcols);

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < ValueUtil.asInt(ntree.asScalar()); i++){
			TreeModel treeModel = encodeTreeModel(trees, i);

			treeModels.add(treeModel);
		}

		Segmentation segmentation = MiningModelUtil.createSegmentation(MultipleModelMethodType.AVERAGE, treeModels);

		MiningSchema miningSchema = ModelUtil.createMiningSchema(this.dataFields);

		Output output = encodeOutput(xrow);

		MiningModel miningModel = new MiningModel(MiningFunctionType.REGRESSION, miningSchema)
			.setSegmentation(segmentation)
			.setOutput(output);

		DataDictionary dataDictionary = new DataDictionary(this.dataFields);

		PMML pmml = new PMML("4.2", createHeader(), dataDictionary)
			.addModels(miningModel);

		return pmml;
	}

	private void initFields(RStringVector xcols){

		// Dependent variable
		{
			DataField dataField = PMMLUtil.createDataField(FieldName.create("pathLength"), false);

			this.dataFields.add(dataField);
		}

		// Independent variables
		for(int i = 0; i < xcols.size(); i++){
			String xcol = xcols.getValue(i);

			DataField dataField = PMMLUtil.createDataField(FieldName.create(xcol), false);

			this.dataFields.add(dataField);
		}
	}

	private TreeModel encodeTreeModel(RGenericVector trees, int index){
		RIntegerVector nrnodes = (RIntegerVector)trees.getValue("nrnodes");
		RIntegerVector ntree = (RIntegerVector)trees.getValue("ntree");
		RIntegerVector nodeStatus = (RIntegerVector)trees.getValue("nodeStatus");
		RIntegerVector leftDaughter = (RIntegerVector)trees.getValue("lDaughter");
		RIntegerVector rightDaughter = (RIntegerVector)trees.getValue("rDaughter");
		RIntegerVector splitAtt = (RIntegerVector)trees.getValue("splitAtt");
		RDoubleVector splitPoint = (RDoubleVector)trees.getValue("splitPoint");
		RIntegerVector nSam = (RIntegerVector)trees.getValue("nSam");

		int rows = nrnodes.asScalar();
		int columns = ntree.asScalar();

		Node root = new Node()
			.setPredicate(new True());

		encodeNode(
			root,
			0,
			0,
			RExpUtil.getColumn(nodeStatus.getValues(), rows, columns, index),
			RExpUtil.getColumn(nSam.getValues(), rows, columns, index),
			RExpUtil.getColumn(leftDaughter.getValues(), rows, columns, index),
			RExpUtil.getColumn(rightDaughter.getValues(), rows, columns, index),
			RExpUtil.getColumn(splitAtt.getValues(), rows, columns, index),
			RExpUtil.getColumn(splitPoint.getValues(), rows, columns, index)
		);

		MiningSchema miningSchema = ModelUtil.createMiningSchema(null, this.dataFields.subList(1, this.dataFields.size()), root);

		TreeModel treeModel = new TreeModel(MiningFunctionType.REGRESSION, miningSchema, root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	private void encodeNode(Node node, int index, int depth, List<Integer> nodeStatus, List<Integer> nodeSize, List<Integer> leftDaughter, List<Integer> rightDaughter, List<Integer> splitAtt, List<Double> splitValue){
		int status = nodeStatus.get(index);
		int size = nodeSize.get(index);

		node.setId(String.valueOf(index + 1));

		// Interior node
		if(status == -3){
			int att = splitAtt.get(index);

			DataField dataField = this.dataFields.get(att);

			Double value = splitValue.get(index);

			Node leftChild = new Node()
				.setPredicate(encodeContinuousSplit(dataField, value, true));

			int leftIndex = (leftDaughter.get(index) - 1);

			encodeNode(leftChild, leftIndex, depth + 1, nodeStatus, nodeSize, leftDaughter, rightDaughter, splitAtt, splitValue);

			Node rightChild = new Node()
				.setPredicate(encodeContinuousSplit(dataField, value, false));

			int rightIndex = (rightDaughter.get(index) - 1);

			encodeNode(rightChild, rightIndex, depth + 1, nodeStatus, nodeSize, leftDaughter, rightDaughter, splitAtt, splitValue);

			node.addNodes(leftChild, rightChild);
		} else

		// Terminal node
		if(status == -1){
			node.setScore(ValueUtil.formatValue(depth + avgPathLength(size)));
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	private Predicate encodeContinuousSplit(DataField dataField, Double split, boolean left){
		SimplePredicate simplePredicate = new SimplePredicate()
			.setField(dataField.getName())
			.setOperator(left ? SimplePredicate.Operator.LESS_THAN : SimplePredicate.Operator.GREATER_OR_EQUAL)
			.setValue(ValueUtil.formatValue(split));

		return simplePredicate;
	}

	private Output encodeOutput(RIntegerVector xrow){
		OutputField rawPathLength = ModelUtil.createPredictedField(FieldName.create("rawPathLength"));

		// "rawPathLength / avgPathLength(xrow)"
		OutputField normalizedPathLength = new OutputField(FieldName.create("normalizedPathLength"))
			.setFeature(FeatureType.TRANSFORMED_VALUE)
			.setDataType(DataType.DOUBLE)
			.setOpType(OpType.CONTINUOUS)
			.setExpression(PMMLUtil.createApply("/", new FieldRef(rawPathLength.getName()), PMMLUtil.createConstant(avgPathLength(xrow.asScalar()))));

		// "2 ^ (-1 * normalizedPathLength)"
		OutputField score = new OutputField(FieldName.create("anomalyScore"))
			.setFeature(FeatureType.TRANSFORMED_VALUE)
			.setDataType(DataType.DOUBLE)
			.setOpType(OpType.CONTINUOUS)
			.setExpression(PMMLUtil.createApply("pow", PMMLUtil.createConstant(2d), PMMLUtil.createApply("*", PMMLUtil.createConstant(-1d), new FieldRef(normalizedPathLength.getName()))));

		Output output = new Output()
			.addOutputFields(rawPathLength, normalizedPathLength, score);

		return output;
	}

	static
	private double avgPathLength(double n){
		double j = (n - 1d);

		if(j <= 0d){
			return 0d;
		}

		return (2d * (Math.log(j) +  0.5772156649d)) - (2d * (j / n));
	}
}
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

import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ResultFeature;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.mining.MiningModelUtil;

public class IForestConverter extends TreeModelConverter<RGenericVector> {

	public IForestConverter(RGenericVector iForest){
		super(iForest);
	}

	@Override
	public void encodeFeatures(FeatureMapper featureMapper){
		RGenericVector iForest = getObject();

		RStringVector xcols = (RStringVector)iForest.getValue("xcols");
		RBooleanVector colisfactor = (RBooleanVector)iForest.getValue("colisfactor");

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

		// Dependent variable
		{
			featureMapper.append(FieldName.create("pathLength"), false);
		}

		// Independent variables
		for(int i = 0; i < xcols.size(); i++){
			String xcol = xcols.getValue(i);

			featureMapper.append(FieldName.create(xcol), false);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector iForest = getObject();

		RGenericVector trees = (RGenericVector)iForest.getValue("trees");
		RDoubleVector ntree = (RDoubleVector)iForest.getValue("ntree");

		if(trees == null){
			throw new IllegalArgumentException();
		}

		RIntegerVector xrow = (RIntegerVector)trees.getValue("xrow");

		Schema segmentSchema = schema.toAnonymousSchema();

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < ValueUtil.asInt(ntree.asScalar()); i++){
			TreeModel treeModel = encodeTreeModel(trees, i, segmentSchema);

			treeModels.add(treeModel);
		}

		Output output = encodeOutput(xrow);

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.AVERAGE, treeModels))
			.setOutput(output);

		return miningModel;
	}

	private TreeModel encodeTreeModel(RGenericVector trees, int index, Schema schema){
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
			RExpUtil.getColumn(splitPoint.getValues(), rows, columns, index),
			schema
		);

		TreeModel treeModel = new TreeModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema), root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	private void encodeNode(Node node, int index, int depth, List<Integer> nodeStatus, List<Integer> nodeSize, List<Integer> leftDaughter, List<Integer> rightDaughter, List<Integer> splitAtt, List<Double> splitValue, Schema schema){
		int status = nodeStatus.get(index);
		int size = nodeSize.get(index);

		node.setId(String.valueOf(index + 1));

		// Interior node
		if(status == -3){
			int att = splitAtt.get(index);

			ContinuousFeature feature = (ContinuousFeature)schema.getFeature(att - 1);

			String value = ValueUtil.formatValue(splitValue.get(index));

			Predicate leftPredicate = createSimplePredicate(feature, SimplePredicate.Operator.LESS_THAN, value);

			Node leftChild = new Node()
				.setPredicate(leftPredicate);

			int leftIndex = (leftDaughter.get(index) - 1);

			encodeNode(leftChild, leftIndex, depth + 1, nodeStatus, nodeSize, leftDaughter, rightDaughter, splitAtt, splitValue, schema);

			Predicate rightPredicate = createSimplePredicate(feature, SimplePredicate.Operator.GREATER_OR_EQUAL, value);

			Node rightChild = new Node()
				.setPredicate(rightPredicate);

			int rightIndex = (rightDaughter.get(index) - 1);

			encodeNode(rightChild, rightIndex, depth + 1, nodeStatus, nodeSize, leftDaughter, rightDaughter, splitAtt, splitValue, schema);

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

	private Output encodeOutput(RIntegerVector xrow){
		OutputField rawPathLength = ModelUtil.createPredictedField(FieldName.create("rawPathLength"), DataType.DOUBLE);

		// "rawPathLength / avgPathLength(xrow)"
		OutputField normalizedPathLength = new OutputField(FieldName.create("normalizedPathLength"), DataType.DOUBLE)
			.setOpType(OpType.CONTINUOUS)
			.setResultFeature(ResultFeature.TRANSFORMED_VALUE)
			.setExpression(PMMLUtil.createApply("/", new FieldRef(rawPathLength.getName()), PMMLUtil.createConstant(avgPathLength(xrow.asScalar()))));

		// "2 ^ (-1 * normalizedPathLength)"
		OutputField score = new OutputField(FieldName.create("anomalyScore"), DataType.DOUBLE)
			.setOpType(OpType.CONTINUOUS)
			.setResultFeature(ResultFeature.TRANSFORMED_VALUE)
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
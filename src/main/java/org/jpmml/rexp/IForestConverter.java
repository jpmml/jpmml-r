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

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.AbstractTransformation;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.Transformation;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.mining.MiningModelUtil;

public class IForestConverter extends TreeModelConverter<RGenericVector> {

	public IForestConverter(RGenericVector iForest){
		super(iForest);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
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

		{
			DataField dataField = encoder.createDataField(FieldName.create("pathLength"), OpType.CONTINUOUS, DataType.DOUBLE);

			encoder.setLabel(dataField);
		}

		for(int i = 0; i < xcols.size(); i++){
			String xcol = xcols.getValue(i);

			DataField dataField = encoder.createDataField(FieldName.create(xcol), OpType.CONTINUOUS, DataType.DOUBLE);

			encoder.addFeature(dataField);
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

		// "rawPathLength / avgPathLength(xrow)"
		Transformation normalizedPathLength = new AbstractTransformation(){

			@Override
			public FieldName getName(FieldName name){
				return FieldName.create("normalizedPathLength");
			}

			@Override
			public Expression createExpression(FieldRef fieldRef){
				return PMMLUtil.createApply("/", fieldRef, PMMLUtil.createConstant(avgPathLength(xrow.asScalar())));
			}
		};

		// "2 ^ (-1 * normalizedPathLength)"
		Transformation anomalyScore = new AbstractTransformation(){

			@Override
			public FieldName getName(FieldName name){
				return FieldName.create("anomalyScore");
			}

			@Override
			public boolean isFinalResult(){
				return true;
			}

			@Override
			public Expression createExpression(FieldRef fieldRef){
				return PMMLUtil.createApply("pow", PMMLUtil.createConstant(2d), PMMLUtil.createApply("*", PMMLUtil.createConstant(-1d), fieldRef));
			}
		};

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema.getLabel()))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.AVERAGE, treeModels))
			.setOutput(ModelUtil.createPredictedOutput(FieldName.create("rawPathLength"), OpType.CONTINUOUS, DataType.DOUBLE, normalizedPathLength, anomalyScore));

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
			FortranMatrixUtil.getColumn(nodeStatus.getValues(), rows, columns, index),
			FortranMatrixUtil.getColumn(nSam.getValues(), rows, columns, index),
			FortranMatrixUtil.getColumn(leftDaughter.getValues(), rows, columns, index),
			FortranMatrixUtil.getColumn(rightDaughter.getValues(), rows, columns, index),
			FortranMatrixUtil.getColumn(splitAtt.getValues(), rows, columns, index),
			FortranMatrixUtil.getColumn(splitPoint.getValues(), rows, columns, index),
			schema
		);

		TreeModel treeModel = new TreeModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema.getLabel()), root)
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

	static
	private double avgPathLength(double n){
		double j = (n - 1d);

		if(j <= 0d){
			return 0d;
		}

		return (2d * (Math.log(j) +  0.5772156649d)) - (2d * (j / n));
	}
}
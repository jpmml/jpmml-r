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
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMMLFunctions;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.tree.BranchNode;
import org.dmg.pmml.tree.LeafNode;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.Transformation;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.mining.MiningModelUtil;
import org.jpmml.converter.transformations.AbstractTransformation;

public class IForestConverter extends TreeModelConverter<RGenericVector> {

	public IForestConverter(RGenericVector iForest){
		super(iForest);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector iForest = getObject();

		RStringVector xcols = iForest.getStringElement("xcols");
		RBooleanVector colisfactor = iForest.getBooleanElement("colisfactor");

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
			DataField dataField = encoder.createDataField("pathLength", OpType.CONTINUOUS, DataType.DOUBLE);

			encoder.setLabel(dataField);
		}

		for(int i = 0; i < xcols.size(); i++){
			String xcol = xcols.getValue(i);

			DataField dataField = encoder.createDataField(xcol, OpType.CONTINUOUS, DataType.DOUBLE);

			encoder.addFeature(dataField);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector iForest = getObject();

		RGenericVector trees = iForest.getGenericElement("trees");
		RDoubleVector ntree = iForest.getDoubleElement("ntree");

		if(trees == null){
			throw new IllegalArgumentException();
		}

		RIntegerVector xrow = trees.getIntegerElement("xrow");

		Schema segmentSchema = schema.toAnonymousSchema();

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < ValueUtil.asInt(ntree.asScalar()); i++){
			TreeModel treeModel = encodeTreeModel(i, trees, segmentSchema);

			treeModels.add(treeModel);
		}

		// "rawPathLength / avgPathLength(xrow)"
		Transformation normalizedPathLength = new AbstractTransformation(){

			@Override
			public String getName(String name){
				return "normalizedPathLength";
			}

			@Override
			public Expression createExpression(FieldRef fieldRef){
				return PMMLUtil.createApply(PMMLFunctions.DIVIDE, fieldRef, PMMLUtil.createConstant(avgPathLength(xrow.asScalar())));
			}
		};

		// "2 ^ (-1 * normalizedPathLength)"
		Transformation anomalyScore = new AbstractTransformation(){

			@Override
			public String getName(String name){
				return "anomalyScore";
			}

			@Override
			public boolean isFinalResult(){
				return true;
			}

			@Override
			public Expression createExpression(FieldRef fieldRef){
				return PMMLUtil.createApply(PMMLFunctions.POW, PMMLUtil.createConstant(2d), PMMLUtil.createApply(PMMLFunctions.MULTIPLY, PMMLUtil.createConstant(-1d), fieldRef));
			}
		};

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema.getLabel()))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.AVERAGE, Segmentation.MissingPredictionTreatment.RETURN_MISSING, treeModels))
			.setOutput(ModelUtil.createPredictedOutput("rawPathLength", OpType.CONTINUOUS, DataType.DOUBLE, normalizedPathLength, anomalyScore));

		return miningModel;
	}

	private TreeModel encodeTreeModel(int index, RGenericVector trees, Schema schema){
		RIntegerVector nrnodes = trees.getIntegerElement("nrnodes");
		RIntegerVector ntree = trees.getIntegerElement("ntree");
		RIntegerVector nodeStatus = trees.getIntegerElement("nodeStatus");
		RIntegerVector leftDaughter = trees.getIntegerElement("lDaughter");
		RIntegerVector rightDaughter = trees.getIntegerElement("rDaughter");
		RIntegerVector splitAtt = trees.getIntegerElement("splitAtt");
		RDoubleVector splitPoint = trees.getDoubleElement("splitPoint");
		RIntegerVector nSam = trees.getIntegerElement("nSam");

		int rows = nrnodes.asScalar();
		int columns = ntree.asScalar();

		Node root = encodeNode(
			0,
			True.INSTANCE,
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

	private Node encodeNode(int index, Predicate predicate, int depth, List<Integer> nodeStatus, List<Integer> nodeSize, List<Integer> leftDaughter, List<Integer> rightDaughter, List<Integer> splitAtt, List<Double> splitValue, Schema schema){
		Integer id = Integer.valueOf(index + 1);

		int status = nodeStatus.get(index);
		int size = nodeSize.get(index);

		// Interior node
		if(status == -3){
			int att = splitAtt.get(index);

			ContinuousFeature feature = (ContinuousFeature)schema.getFeature(att - 1);

			Double value = splitValue.get(index);

			Predicate leftPredicate = createSimplePredicate(feature, SimplePredicate.Operator.LESS_THAN, value);
			Predicate rightPredicate = createSimplePredicate(feature, SimplePredicate.Operator.GREATER_OR_EQUAL, value);

			Node leftChild = encodeNode(leftDaughter.get(index) - 1, leftPredicate, depth + 1, nodeStatus, nodeSize, leftDaughter, rightDaughter, splitAtt, splitValue, schema);
			Node rightChild = encodeNode(rightDaughter.get(index) - 1, rightPredicate, depth + 1, nodeStatus, nodeSize, leftDaughter, rightDaughter, splitAtt, splitValue, schema);

			Node result = new BranchNode(null, predicate)
				.setId(id)
				.addNodes(leftChild, rightChild);

			return result;
		} else

		// Terminal node
		if(status == -1){
			Node result = new LeafNode(depth + avgPathLength(size), predicate)
				.setId(id);

			return result;
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
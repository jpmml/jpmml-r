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
import java.util.List;

import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ResultFeature;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.Target;
import org.dmg.pmml.Targets;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ListFeature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.mining.MiningModelUtil;

public class GBMConverter extends TreeModelConverter<RGenericVector> {

	public GBMConverter(RGenericVector gbm){
		super(gbm);
	}

	@Override
	public void encodeFeatures(FeatureMapper featureMapper){
		RGenericVector gbm = getObject();

		RGenericVector distribution = (RGenericVector)gbm.getValue("distribution");
		RStringVector response_name = (RStringVector)gbm.getValue("response.name", true);
		RGenericVector var_levels = (RGenericVector)gbm.getValue("var.levels");
		RStringVector var_names = (RStringVector)gbm.getValue("var.names");
		RNumberVector<?> var_type = (RNumberVector<?>)gbm.getValue("var.type");
		RStringVector classes = (RStringVector)gbm.getValue("classes", true);

		// Dependent variable
		{
			FieldName responseName;

			if(response_name != null){
				responseName = FieldName.create(response_name.asScalar());
			} else

			{
				responseName = FieldName.create("y");
			}

			RStringVector distributionName = (RStringVector)distribution.getValue("name");

			switch(distributionName.asScalar()){
				case "gaussian":
					featureMapper.append(responseName, false);
					break;
				case "adaboost":
				case "bernoulli":
					featureMapper.append(responseName, GBMConverter.BINARY_CLASSES);
					break;
				case "multinomial":
					featureMapper.append(responseName, classes.getValues());
					break;
				default:
					throw new IllegalArgumentException();
			}
		}

		// Independent variables
		for(int i = 0; i < var_names.size(); i++){
			FieldName varName = FieldName.create(var_names.getValue(i));

			boolean categorical = (ValueUtil.asInt(var_type.getValue(i)) > 0);
			if(categorical){
				RStringVector var_level = (RStringVector)var_levels.getValue(i);

				featureMapper.append(varName, var_level.getValues());
			} else

			{
				featureMapper.append(varName, false);
			}
		}
	}

	@Override
	public MiningModel encodeModel(Schema schema){
		RGenericVector gbm = getObject();

		RDoubleVector initF = (RDoubleVector)gbm.getValue("initF");
		RGenericVector trees = (RGenericVector)gbm.getValue("trees");
		RGenericVector c_splits = (RGenericVector)gbm.getValue("c.splits");
		RGenericVector distribution = (RGenericVector)gbm.getValue("distribution");

		RStringVector distributionName = (RStringVector)distribution.getValue("name");

		Schema segmentSchema = schema.toAnonymousSchema();

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < trees.size(); i++){
			RGenericVector tree = (RGenericVector)trees.getValue(i);

			TreeModel treeModel = encodeTreeModel(MiningFunction.REGRESSION, tree, c_splits, segmentSchema);

			treeModels.add(treeModel);
		}

		MiningModel miningModel = encodeMiningModel(distributionName, treeModels, initF.asScalar(), schema);

		return miningModel;
	}

	private MiningModel encodeMiningModel(RStringVector distributionName, List<TreeModel> treeModels, Double initF, Schema schema){

		switch(distributionName.asScalar()){
			case "gaussian":
				return encodeRegression(treeModels, initF, schema);
			case "adaboost":
				return encodeBinaryClassification(treeModels, initF, -2d, schema);
			case "bernoulli":
				return encodeBinaryClassification(treeModels, initF, -1d, schema);
			case "multinomial":
				return encodeMultinomialClassification(treeModels, initF, schema);
			default:
				break;
		}

		throw new IllegalArgumentException();
	}

	private MiningModel encodeRegression(List<TreeModel> treeModels, Double initF, Schema schema){
		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.SUM, treeModels))
			.setTargets(createTargets(initF, schema));

		return miningModel;
	}

	private MiningModel encodeBinaryClassification(List<TreeModel> treeModels, Double initF, double coefficient, Schema schema){
		Schema segmentSchema = schema.toAnonymousSchema();

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(segmentSchema))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.SUM, treeModels))
			.setTargets(createTargets(initF, segmentSchema))
			.setOutput(createOutput(null));

		return MiningModelUtil.createBinaryLogisticClassification(schema, miningModel, coefficient, true);
	}

	private MiningModel encodeMultinomialClassification(List<TreeModel> treeModels, Double initF, Schema schema){
		Schema segmentSchema = schema.toAnonymousSchema();

		List<Model> miningModels = new ArrayList<>();

		List<String> targetCategories = schema.getTargetCategories();
		for(int i = 0; i < targetCategories.size(); i++){
			String targetCategory = targetCategories.get(i);

			List<TreeModel> segmentTreeModels = getColumn(treeModels, i, (treeModels.size() / targetCategories.size()), targetCategories.size());

			MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(segmentSchema))
				.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.SUM, segmentTreeModels))
				.setTargets(createTargets(initF, segmentSchema))
				.setOutput(createOutput(targetCategory));

			miningModels.add(miningModel);
		}

		return MiningModelUtil.createClassification(schema, miningModels, RegressionModel.NormalizationMethod.SOFTMAX, true);
	}

	private TreeModel encodeTreeModel(MiningFunction miningFunction, RGenericVector tree, RGenericVector c_splits, Schema schema){
		Node root = new Node()
			.setId("1")
			.setPredicate(new True());

		encodeNode(root, 0, tree, c_splits, schema);

		TreeModel treeModel = new TreeModel(miningFunction, ModelUtil.createMiningSchema(schema), root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.MULTI_SPLIT);

		return treeModel;
	}

	private void encodeNode(Node node, int i, RGenericVector tree, RGenericVector c_splits, Schema schema){
		RIntegerVector splitVar = (RIntegerVector)tree.getValue(0);
		RDoubleVector splitCodePred = (RDoubleVector)tree.getValue(1);
		RIntegerVector leftNode = (RIntegerVector)tree.getValue(2);
		RIntegerVector rightNode = (RIntegerVector)tree.getValue(3);
		RIntegerVector missingNode = (RIntegerVector)tree.getValue(4);
		RDoubleVector prediction = (RDoubleVector)tree.getValue(7);

		Predicate missingPredicate;

		Predicate leftPredicate;
		Predicate rightPredicate;

		Integer var = splitVar.getValue(i);
		if(var != -1){
			Feature feature = schema.getFeature(var);

			missingPredicate = createSimplePredicate(feature, SimplePredicate.Operator.IS_MISSING, null);

			Double split = splitCodePred.getValue(i);

			if(feature instanceof ListFeature){
				ListFeature listFeature = (ListFeature)feature;

				List<String> values = listFeature.getValues();

				int index = ValueUtil.asInt(split);

				RIntegerVector c_split = (RIntegerVector)c_splits.getValue(index);

				List<Integer> splitValues = c_split.getValues();

				leftPredicate = createSimpleSetPredicate(listFeature, selectValues(values, splitValues, true));
				rightPredicate = createSimpleSetPredicate(listFeature, selectValues(values, splitValues, false));
			} else

			if(feature instanceof ContinuousFeature){
				String value = ValueUtil.formatValue(split);

				leftPredicate = createSimplePredicate(feature, SimplePredicate.Operator.LESS_THAN, value);
				rightPredicate = createSimplePredicate(feature, SimplePredicate.Operator.GREATER_OR_EQUAL, value);
			} else

			{
				throw new IllegalArgumentException();
			}
		} else

		{
			Double value = prediction.getValue(i);

			node.setScore(ValueUtil.formatValue(value));

			return;
		}

		Integer missing = missingNode.getValue(i);
		if(missing != -1){
			Node missingChild = new Node()
				.setId(String.valueOf(missing + 1))
				.setPredicate(missingPredicate);

			encodeNode(missingChild, missing, tree, c_splits, schema);

			node.addNodes(missingChild);
		}

		Integer left = leftNode.getValue(i);
		if(left != -1){
			Node leftChild = new Node()
				.setId(String.valueOf(left + 1))
				.setPredicate(leftPredicate);

			encodeNode(leftChild, left, tree, c_splits, schema);

			node.addNodes(leftChild);
		}

		Integer right = rightNode.getValue(i);
		if(right != -1){
			Node rightChild = new Node()
				.setId(String.valueOf(right + 1))
				.setPredicate(rightPredicate);

			encodeNode(rightChild, right, tree, c_splits, schema);

			node.addNodes(rightChild);
		}
	}

	static
	private Targets createTargets(Double initF, Schema schema){

		if(!ValueUtil.isZero(initF)){
			Target target = ModelUtil.createRescaleTarget(schema.getTargetField(), null, initF);

			Targets targets = new Targets()
				.addTargets(target);

			return targets;
		}

		return null;
	}

	static
	private Output createOutput(String targetCategory){
		OutputField gbmField = new OutputField(FieldName.create("gbmValue" + (targetCategory != null ? ("_" + targetCategory) : "")), DataType.DOUBLE)
			.setOpType(OpType.CONTINUOUS)
			.setResultFeature(ResultFeature.PREDICTED_VALUE)
			.setFinalResult(false);

		Output output = new Output()
			.addOutputFields(gbmField);

		return output;
	}

	static
	private <E> List<E> selectValues(List<E> values, List<Integer> splitValues, boolean left){

		if(values.size() != splitValues.size()){
			throw new IllegalArgumentException();
		}

		List<E> result = new ArrayList<>();

		for(int i = 0; i < values.size(); i++){
			E value = values.get(i);
			Integer splitValue = splitValues.get(i);

			boolean append;

			if(left){
				append = (splitValue == -1);
			} else

			{
				append = (splitValue == 1);
			} // End if

			if(append){
				result.add(value);
			}
		}

		return result;
	}

	static
	private <E> List<E> getColumn(List<E> values, int index, int rows, int columns){

		if(values.size() != (rows * columns)){
			throw new IllegalArgumentException();
		}

		List<E> result = new ArrayList<>(rows);

		for(int row = 0; row < rows; row++){
			E value = values.get((row * columns) + index);

			result.add(value);
		}

		return result;
	}

	private static final List<String> BINARY_CLASSES = Arrays.asList("0", "1");
}
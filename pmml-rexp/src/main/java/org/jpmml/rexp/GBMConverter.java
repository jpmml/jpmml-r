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

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.tree.BranchNode;
import org.dmg.pmml.tree.LeafNode;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.CMatrixUtil;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.CategoryManager;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FieldNameUtil;
import org.jpmml.converter.FlagManager;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.mining.MiningModelUtil;

public class GBMConverter extends TreeModelConverter<RGenericVector> {

	public GBMConverter(RGenericVector gbm){
		super(gbm);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector gbm = getObject();

		RGenericVector distribution = gbm.getGenericElement("distribution");
		RStringVector response_name = gbm.getStringElement("response.name", false);
		RGenericVector var_levels = gbm.getGenericElement("var.levels");
		RStringVector var_names = gbm.getStringElement("var.names");
		RNumberVector<?> var_type = gbm.getNumericElement("var.type");
		RStringVector classes = gbm.getStringElement("classes", false);

		RStringVector distributionName = distribution.getStringElement("name");

		RVectorUtil.checkSize(var_names, var_type);

		{
			String responseName;

			if(response_name != null){
				responseName = response_name.asScalar();
			} else

			{
				responseName = "y";
			}

			DataField dataField;

			switch(distributionName.asScalar()){
				case "gaussian":
					dataField = encoder.createDataField(responseName, OpType.CONTINUOUS, DataType.DOUBLE);
					break;
				case "adaboost":
				case "bernoulli":
					dataField = encoder.createDataField(responseName, OpType.CATEGORICAL, DataType.INTEGER, GBMConverter.BINARY_CLASSES);
					break;
				case "multinomial":
					dataField = encoder.createDataField(responseName, OpType.CATEGORICAL, DataType.STRING, classes.getValues());
					break;
				default:
					throw new IllegalArgumentException();
			}

			encoder.setLabel(dataField);
		}

		for(int i = 0; i < var_names.size(); i++){
			String varName = var_names.getValue(i);

			DataField dataField;

			boolean categorical = (ValueUtil.asInt(var_type.getValue(i)) > 0);
			if(categorical){
				RStringVector var_level = var_levels.getStringValue(i);

				dataField = encoder.createDataField(varName, OpType.CATEGORICAL, DataType.STRING, var_level.getValues());
			} else

			{
				dataField = encoder.createDataField(varName, OpType.CONTINUOUS, DataType.DOUBLE);
			}

			encoder.addFeature(dataField);
		}
	}

	@Override
	public MiningModel encodeModel(Schema schema){
		RGenericVector gbm = getObject();

		RDoubleVector initF = gbm.getDoubleElement("initF");
		RGenericVector trees = gbm.getGenericElement("trees");
		RGenericVector c_splits = gbm.getGenericElement("c.splits");
		RGenericVector distribution = gbm.getGenericElement("distribution");

		RStringVector distributionName = distribution.getStringElement("name");

		Schema segmentSchema = schema.toAnonymousRegressorSchema(DataType.DOUBLE);

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < trees.size(); i++){
			RGenericVector tree = trees.getGenericValue(i);

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
				throw new IllegalArgumentException();
		}
	}

	private MiningModel encodeRegression(List<TreeModel> treeModels, Double initF, Schema schema){
		MiningModel miningModel = createMiningModel(treeModels, initF, schema);

		return miningModel;
	}

	private MiningModel encodeBinaryClassification(List<TreeModel> treeModels, Double initF, double coefficient, Schema schema){
		Schema segmentSchema = schema.toAnonymousRegressorSchema(DataType.DOUBLE);

		MiningModel miningModel = createMiningModel(treeModels, initF, segmentSchema)
			.setOutput(ModelUtil.createPredictedOutput("gbmValue", OpType.CONTINUOUS, DataType.DOUBLE));

		return MiningModelUtil.createBinaryLogisticClassification(miningModel, -coefficient, 0d, RegressionModel.NormalizationMethod.LOGIT, true, schema);
	}

	private MiningModel encodeMultinomialClassification(List<TreeModel> treeModels, Double initF, Schema schema){
		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		Schema segmentSchema = schema.toAnonymousRegressorSchema(DataType.DOUBLE);

		List<Model> miningModels = new ArrayList<>();

		for(int i = 0, columns = categoricalLabel.size(), rows = (treeModels.size() / columns); i < columns; i++){
			MiningModel miningModel = createMiningModel(CMatrixUtil.getColumn(treeModels, rows, columns, i), initF, segmentSchema)
				.setOutput(ModelUtil.createPredictedOutput(FieldNameUtil.create("gbmValue", categoricalLabel.getValue(i)), OpType.CONTINUOUS, DataType.DOUBLE));

			miningModels.add(miningModel);
		}

		return MiningModelUtil.createClassification(miningModels, RegressionModel.NormalizationMethod.SOFTMAX, true, schema);
	}

	private TreeModel encodeTreeModel(MiningFunction miningFunction, RGenericVector tree, RGenericVector c_splits, Schema schema){
		Node root = encodeNode(0, True.INSTANCE, tree, c_splits, new FlagManager(), new CategoryManager(), schema);

		TreeModel treeModel = new TreeModel(miningFunction, ModelUtil.createMiningSchema(schema.getLabel()), root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.MULTI_SPLIT);

		return treeModel;
	}

	private Node encodeNode(int i, Predicate predicate, RGenericVector tree, RGenericVector c_splits, FlagManager flagManager, CategoryManager categoryManager, Schema schema){
		Integer id = Integer.valueOf(i + 1);

		RIntegerVector splitVar = tree.getIntegerValue(0);
		RDoubleVector splitCodePred = tree.getDoubleValue(1);
		RIntegerVector leftNode = tree.getIntegerValue(2);
		RIntegerVector rightNode = tree.getIntegerValue(3);
		RIntegerVector missingNode = tree.getIntegerValue(4);
		RDoubleVector prediction = tree.getDoubleValue(7);

		Integer var = splitVar.getValue(i);
		if(var == -1){
			Double value = prediction.getValue(i);

			Node result = new LeafNode(value, predicate)
				.setId(id);

			return result;
		}

		Boolean isMissing;

		FlagManager missingFlagManager = flagManager;
		FlagManager nonMissingFlagManager = flagManager;

		Predicate missingPredicate;

		Feature feature = schema.getFeature(var);

		{
			String name = feature.getName();

			isMissing = flagManager.getValue(name);
			if(isMissing == null){
				missingFlagManager = missingFlagManager.fork(name, Boolean.TRUE);
				nonMissingFlagManager = nonMissingFlagManager.fork(name, Boolean.FALSE);
			}

			missingPredicate = createSimplePredicate(feature, SimplePredicate.Operator.IS_MISSING, null);
		}

		CategoryManager leftCategoryManager = categoryManager;
		CategoryManager rightCategoryManager = categoryManager;

		Predicate leftPredicate;
		Predicate rightPredicate;

		Double split = splitCodePred.getValue(i);

		if(feature instanceof CategoricalFeature){
			CategoricalFeature categoricalFeature = (CategoricalFeature)feature;

			String name = categoricalFeature.getName();
			List<?> values = categoricalFeature.getValues();

			int index = ValueUtil.asInt(split);

			RIntegerVector c_split = c_splits.getIntegerValue(index);

			List<Integer> splitValues = c_split.getValues();

			java.util.function.Predicate<Object> valueFilter = categoryManager.getValueFilter(name);

			List<Object> leftValues = selectValues(values, valueFilter, splitValues, true);
			List<Object> rightValues = selectValues(values, valueFilter, splitValues, false);

			leftCategoryManager = leftCategoryManager.fork(name, leftValues);
			rightCategoryManager = rightCategoryManager.fork(name, rightValues);

			leftPredicate = createPredicate(categoricalFeature, leftValues);
			rightPredicate = createPredicate(categoricalFeature, rightValues);
		} else

		{
			ContinuousFeature continuousFeature = feature.toContinuousFeature();

			leftPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.LESS_THAN, split);
			rightPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.GREATER_OR_EQUAL, split);
		}

		Node result = new BranchNode(null, predicate)
			.setId(id);

		List<Node> nodes = result.getNodes();

		Integer missing = missingNode.getValue(i);
		if(missing != -1 && (isMissing == null || isMissing)){
			Node missingChild = encodeNode(missing, missingPredicate, tree, c_splits, missingFlagManager, categoryManager, schema);

			nodes.add(missingChild);
		}

		Integer left = leftNode.getValue(i);
		if(left != -1 && (isMissing == null || !isMissing)){
			Node leftChild = encodeNode(left, leftPredicate, tree, c_splits, nonMissingFlagManager, leftCategoryManager, schema);

			nodes.add(leftChild);
		}

		Integer right = rightNode.getValue(i);
		if(right != -1 && (isMissing == null || !isMissing)){
			Node rightChild = encodeNode(right, rightPredicate, tree, c_splits, nonMissingFlagManager, rightCategoryManager, schema);

			nodes.add(rightChild);
		}

		return result;
	}

	static
	private MiningModel createMiningModel(List<TreeModel> treeModels, Double initF, Schema schema){
		ContinuousLabel continuousLabel = (ContinuousLabel)schema.getLabel();

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(continuousLabel))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.SUM, Segmentation.MissingPredictionTreatment.RETURN_MISSING, treeModels))
			.setTargets(ModelUtil.createRescaleTargets(null, initF, continuousLabel));

		return miningModel;
	}

	static
	private List<Object> selectValues(List<?> values, java.util.function.Predicate<Object> valueFilter, List<Integer> splitValues, boolean left){

		if(values.size() != splitValues.size()){
			throw new IllegalArgumentException();
		}

		List<Object> result = new ArrayList<>();

		for(int i = 0; i < values.size(); i++){
			Object value = values.get(i);
			Integer splitValue = splitValues.get(i);

			boolean append;

			if(left){
				append = (splitValue == -1);
			} else

			{
				append = (splitValue == 1);
			} // End if

			if(append && valueFilter.test(value)){
				result.add(value);
			}
		}

		return result;
	}

	private static final List<Integer> BINARY_CLASSES = Arrays.asList(0, 1);
}
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.dmg.pmml.DataDictionary;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FeatureType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunctionType;
import org.dmg.pmml.MiningModel;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Model;
import org.dmg.pmml.MultipleModelMethodType;
import org.dmg.pmml.Node;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.RegressionNormalizationMethodType;
import org.dmg.pmml.Segment;
import org.dmg.pmml.Segmentation;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.Targets;
import org.dmg.pmml.TreeModel;
import org.dmg.pmml.TreeModel.SplitCharacteristic;
import org.dmg.pmml.True;
import org.dmg.pmml.Value;
import org.jpmml.converter.ElementKey;
import org.jpmml.converter.MiningModelUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.ValueUtil;

public class GBMConverter extends Converter {

	private List<DataField> dataFields = new ArrayList<>();

	private LoadingCache<ElementKey, Predicate> predicateCache = CacheBuilder.newBuilder()
		.build(new CacheLoader<ElementKey, Predicate>(){

			@Override
			public Predicate load(ElementKey key){
				Object[] content = key.getContent();

				return encodeCategoricalSplit((DataField)content[0], (List<Integer>)content[1], (Boolean)content[2]);
			}
		});


	@Override
	public PMML convert(RExp rexp){
		return convert((RGenericVector)rexp);
	}

	private PMML convert(RGenericVector gbm){
		RDoubleVector initF = (RDoubleVector)gbm.getValue("initF");
		RGenericVector trees = (RGenericVector)gbm.getValue("trees");
		RGenericVector c_splits = (RGenericVector)gbm.getValue("c.splits");
		RGenericVector distribution = (RGenericVector)gbm.getValue("distribution");
		RGenericVector var_levels = (RGenericVector)gbm.getValue("var.levels");
		RStringVector var_names = (RStringVector)gbm.getValue("var.names");
		RNumberVector<?> var_type = (RNumberVector<?>)gbm.getValue("var.type");

		RStringVector response_name;

		try {
			response_name = (RStringVector)gbm.getValue("response.name");
		} catch(IllegalArgumentException iae){
			response_name = null;
		}

		RStringVector classes;

		try {
			classes = (RStringVector)gbm.getValue("classes");
		} catch(IllegalArgumentException iae){
			classes = null;
		}

		RStringVector name = (RStringVector)distribution.getValue("name");

		initFields(name, response_name, classes, var_names, var_type, var_levels);

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < trees.size(); i++){
			RGenericVector tree = (RGenericVector)trees.getValue(i);

			TreeModel treeModel = encodeTreeModel(MiningFunctionType.REGRESSION, tree, c_splits);

			treeModels.add(treeModel);
		}

		Segmentation segmentation = MiningModelUtil.createSegmentation(MultipleModelMethodType.SUM, treeModels);

		MiningModel miningModel = encodeMiningModel(name, classes, segmentation, initF.asScalar());

		DataDictionary dataDictionary = new DataDictionary(this.dataFields);

		PMML pmml = new PMML("4.2", createHeader(), dataDictionary)
			.addModels(miningModel);

		return pmml;
	}

	private void initFields(RStringVector distribution, RStringVector response_name, RStringVector classes, RStringVector var_names, RNumberVector<?> var_type, RGenericVector var_levels){

		// Dependent variable
		{
			FieldName responseName;

			if(response_name != null){
				responseName = FieldName.create(response_name.asScalar());
			} else

			{
				responseName = FieldName.create("y");
			}

			DataField dataField;

			switch(distribution.asScalar()){
				case "gaussian":
					{
						dataField = PMMLUtil.createDataField(responseName, false);
					}
					break;
				case "adaboost":
				case "bernoulli":
					{
						dataField = PMMLUtil.createDataField(responseName, true);

						List<Value> values = dataField.getValues();
						values.addAll(PMMLUtil.createValues(GBMConverter.BINARY_CLASSES));
					}
					break;
				case "multinomial":
					{
						dataField = PMMLUtil.createDataField(responseName, true);

						List<Value> values = dataField.getValues();
						values.addAll(PMMLUtil.createValues(classes.getValues()));
					}
					break;
				default:
					throw new IllegalArgumentException();
			}

			this.dataFields.add(dataField);
		}

		// Independent variables
		for(int i = 0; i < var_names.size(); i++){
			FieldName varName = FieldName.create(var_names.getValue(i));

			boolean categorical = (ValueUtil.asInt(var_type.getValue(i)) > 0);

			DataField dataField = PMMLUtil.createDataField(varName, categorical);

			if(categorical){
				RStringVector var_level = (RStringVector)var_levels.getValue(i);

				List<Value> values = dataField.getValues();
				values.addAll(PMMLUtil.createValues(var_level.getValues()));

				dataField = PMMLUtil.refineDataField(dataField);
			}

			this.dataFields.add(dataField);
		}
	}

	private MiningModel encodeMiningModel(RStringVector distribution, RStringVector classes, Segmentation segmentation, Double initF){

		switch(distribution.asScalar()){
			case "gaussian":
				return encodeRegression(segmentation, initF);
			case "adaboost":
				return encodeBinaryClassification(segmentation, initF, -2d);
			case "bernoulli":
				return encodeBinaryClassification(segmentation, initF, -1d);
			case "multinomial":
				return encodeMultinomialClassification(classes, segmentation, initF);
			default:
				break;
		}

		throw new IllegalArgumentException();
	}

	private MiningModel encodeRegression(Segmentation segmentation, Double initF){
		DataField dataField = this.dataFields.get(0);

		MiningSchema miningSchema = ModelUtil.createMiningSchema(this.dataFields);

		Targets targets = new Targets()
			.addTargets(ModelUtil.createRescaleTarget(dataField, null, initF));

		MiningModel miningModel = new MiningModel(MiningFunctionType.REGRESSION, miningSchema)
			.setSegmentation(segmentation)
			.setTargets(targets);

		return miningModel;
	}

	private MiningModel encodeBinaryClassification(Segmentation segmentation, Double initF, double coefficient){
		DataField dataField = this.dataFields.get(0);

		FieldName targetField = dataField.getName();

		List<FieldName> activeFields = PMMLUtil.getNames(this.dataFields.subList(1, this.dataFields.size()));

		MiningSchema miningSchema = ModelUtil.createMiningSchema(null, activeFields);

		OutputField rawGbmValue = ModelUtil.createPredictedField(FieldName.create("rawGbmValue"));

		OutputField scaledGbmValue = new OutputField(FieldName.create("scaledGbmValue"))
			.setFeature(FeatureType.TRANSFORMED_VALUE)
			.setDataType(DataType.DOUBLE)
			.setOpType(OpType.CONTINUOUS)
			.setExpression(encodeScalingExpression(rawGbmValue.getName(), initF));

		Output output = new Output()
			.addOutputFields(rawGbmValue, scaledGbmValue);

		MiningModel miningModel = new MiningModel(MiningFunctionType.REGRESSION, miningSchema)
			.setSegmentation(segmentation)
			.setOutput(output);

		return MiningModelUtil.createBinaryLogisticClassification(targetField, GBMConverter.BINARY_CLASSES, activeFields, miningModel, coefficient, true);
	}

	private MiningModel encodeMultinomialClassification(RStringVector classes, Segmentation segmentation, Double initF){
		DataField dataField = this.dataFields.get(0);

		FieldName targetField = dataField.getName();

		List<FieldName> activeFields = PMMLUtil.getNames(this.dataFields.subList(1, this.dataFields.size()));

		MiningSchema valueMiningSchema = ModelUtil.createMiningSchema(null, activeFields);

		List<Segment> segments = segmentation.getSegments();

		List<Model> models = new ArrayList<>();

		for(int i = 0; i < classes.size(); i++){
			String value = classes.getValue(i);

			OutputField rawGbmValue = ModelUtil.createPredictedField(FieldName.create("rawGbmValue_" + value));

			OutputField transformedGbmValue = new OutputField(FieldName.create("transformedGbmValue_" + value))
				.setFeature(FeatureType.TRANSFORMED_VALUE)
				.setDataType(DataType.DOUBLE)
				.setOpType(OpType.CONTINUOUS)
				.setExpression(encodeScalingExpression(rawGbmValue.getName(), initF));

			List<Segment> valueSegments = getColumn(segments, i, (segments.size() / classes.size()), classes.size());

			Segmentation valueSegmentation = new Segmentation(MultipleModelMethodType.SUM, valueSegments);

			Output valueOutput = new Output()
				.addOutputFields(rawGbmValue, transformedGbmValue);

			MiningModel valueMiningModel = new MiningModel(MiningFunctionType.REGRESSION, valueMiningSchema)
				.setSegmentation(valueSegmentation)
				.setOutput(valueOutput);

			models.add(valueMiningModel);
		}

		MiningModel miningModel = MiningModelUtil.createClassification(targetField, classes.getValues(), activeFields, models, RegressionNormalizationMethodType.SOFTMAX, true);

		return miningModel;
	}

	private TreeModel encodeTreeModel(MiningFunctionType miningFunction, RGenericVector tree, RGenericVector c_splits){
		Node root = new Node()
			.setId("1")
			.setPredicate(new True());

		encodeNode(root, 0, tree, c_splits);

		MiningSchema miningSchema = ModelUtil.createMiningSchema(null, this.dataFields.subList(1, this.dataFields.size()), root);

		TreeModel treeModel = new TreeModel(miningFunction, miningSchema, root)
			.setSplitCharacteristic(SplitCharacteristic.MULTI_SPLIT);

		return treeModel;
	}

	private void encodeNode(Node node, int i, RGenericVector tree, RGenericVector c_splits){
		RIntegerVector splitVar = (RIntegerVector)tree.getValue(0);
		RDoubleVector splitCodePred = (RDoubleVector)tree.getValue(1);
		RIntegerVector leftNode = (RIntegerVector)tree.getValue(2);
		RIntegerVector rightNode = (RIntegerVector)tree.getValue(3);
		RIntegerVector missingNode = (RIntegerVector)tree.getValue(4);
		RDoubleVector prediction = (RDoubleVector)tree.getValue(7);

		Predicate missingPredicate = null;

		Predicate leftPredicate = null;
		Predicate rightPredicate = null;

		Integer var = splitVar.getValue(i);
		if(var != -1){
			DataField dataField = this.dataFields.get(var + 1);

			missingPredicate = encodeIsMissingSplit(dataField);

			Double split = splitCodePred.getValue(i);

			OpType opType = dataField.getOpType();
			switch(opType){
				case CATEGORICAL:
					int index = ValueUtil.asInt(split);

					RIntegerVector c_split = (RIntegerVector)c_splits.getValue(index);

					List<Integer> splitValues = c_split.getValues();

					leftPredicate = this.predicateCache.getUnchecked(new ElementKey(dataField, splitValues, Boolean.TRUE));
					rightPredicate = this.predicateCache.getUnchecked(new ElementKey(dataField, splitValues, Boolean.FALSE));
					break;
				case CONTINUOUS:
					leftPredicate = encodeContinuousSplit(dataField, split, true);
					rightPredicate = encodeContinuousSplit(dataField, split, false);
					break;
				default:
					throw new IllegalArgumentException();
			}
		} else

		{
			Double value = prediction.getValue(i);

			node.setScore(ValueUtil.formatValue(value));
		}

		Integer missing = missingNode.getValue(i);
		if(missing != -1){
			Node missingChild = new Node()
				.setId(String.valueOf(missing + 1))
				.setPredicate(missingPredicate);

			encodeNode(missingChild, missing, tree, c_splits);

			node.addNodes(missingChild);
		}

		Integer left = leftNode.getValue(i);
		if(left != -1){
			Node leftChild = new Node()
				.setId(String.valueOf(left + 1))
				.setPredicate(leftPredicate);

			encodeNode(leftChild, left, tree, c_splits);

			node.addNodes(leftChild);
		}

		Integer right = rightNode.getValue(i);
		if(right != -1){
			Node rightChild = new Node()
				.setId(String.valueOf(right + 1))
				.setPredicate(rightPredicate);

			encodeNode(rightChild, right, tree, c_splits);

			node.addNodes(rightChild);
		}
	}

	static
	private Predicate encodeIsMissingSplit(DataField dataField){
		SimplePredicate simplePredicate = new SimplePredicate()
			.setField(dataField.getName())
			.setOperator(SimplePredicate.Operator.IS_MISSING);

		return simplePredicate;
	}

	static
	private Predicate encodeCategoricalSplit(DataField dataField, List<Integer> splitValues, boolean left){
		List<Value> values = selectValues(dataField.getValues(), splitValues, left);

		if(values.size() == 1){
			Value value = values.get(0);

			SimplePredicate simplePredicate = new SimplePredicate()
				.setField(dataField.getName())
				.setOperator(SimplePredicate.Operator.EQUAL)
				.setValue(value.getValue());

			return simplePredicate;
		}

		SimpleSetPredicate simpleSetPredicate = new SimpleSetPredicate()
			.setField(dataField.getName())
			.setBooleanOperator(SimpleSetPredicate.BooleanOperator.IS_IN)
			.setArray(PMMLUtil.createArray(dataField.getDataType(), values));

		return simpleSetPredicate;
	}

	static
	private Predicate encodeContinuousSplit(DataField dataField, Double split, boolean left){
		SimplePredicate simplePredicate = new SimplePredicate()
			.setField(dataField.getName())
			.setOperator(left ? SimplePredicate.Operator.LESS_THAN : SimplePredicate.Operator.GREATER_OR_EQUAL)
			.setValue(ValueUtil.formatValue(split));

		return simplePredicate;
	}

	static
	private Expression encodeScalingExpression(FieldName name, Double initF){
		Expression expression = new FieldRef(name);

		if(!ValueUtil.isZero(initF)){
			expression = PMMLUtil.createApply("+", expression, PMMLUtil.createConstant(initF));
		}

		return expression;
	}

	static
	private List<Value> selectValues(List<Value> values, List<Integer> splitValues, boolean left){

		if(values.size() != splitValues.size()){
			throw new IllegalArgumentException();
		}

		List<Value> result = new ArrayList<>();

		for(int i = 0; i < values.size(); i++){
			Value value = values.get(i);

			boolean append;

			if(left){
				append = (splitValues.get(i) == -1);
			} else

			{
				append = (splitValues.get(i) == 1);
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
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
import com.google.common.collect.Lists;
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
		RStringVector response_name = (RStringVector)gbm.getValue("response.name");
		RGenericVector var_levels = (RGenericVector)gbm.getValue("var.levels");
		RStringVector var_names = (RStringVector)gbm.getValue("var.names");
		RDoubleVector var_type = (RDoubleVector)gbm.getValue("var.type");

		RStringVector name = (RStringVector)distribution.getValue("name");

		initFields(name, response_name, var_names, var_type, var_levels);

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < trees.size(); i++){
			RGenericVector tree = (RGenericVector)trees.getValue(i);

			TreeModel treeModel = encodeTreeModel(MiningFunctionType.REGRESSION, tree, c_splits);

			treeModels.add(treeModel);
		}

		Segmentation segmentation = MiningModelUtil.createSegmentation(MultipleModelMethodType.SUM, treeModels);

		MiningModel miningModel = encodeMiningModel(name, segmentation, initF.getValue(0));

		DataDictionary dataDictionary = new DataDictionary(this.dataFields);

		PMML pmml = new PMML("4.2", createHeader(), dataDictionary)
			.addModels(miningModel);

		return pmml;
	}

	private void initFields(RStringVector distribution, RStringVector response_name, RStringVector var_names, RDoubleVector var_type, RGenericVector var_levels){

		// Dependent variable
		{
			FieldName responseName = FieldName.create(response_name.asScalar());

			DataField dataField;

			switch(distribution.asScalar()){
				case "adaboost":
				case "bernoulli":
					dataField = PMMLUtil.createDataField(responseName, true);

					List<Value> values = dataField.getValues();
					values.addAll(PMMLUtil.createValues(GBMConverter.BINARY_CLASSES));
					break;
				case "gaussian":
					dataField = PMMLUtil.createDataField(responseName, false);
					break;
				default:
					throw new IllegalArgumentException();
			}

			this.dataFields.add(dataField);
		}

		// Independent variables
		for(int i = 0; i < var_names.size(); i++){
			FieldName varName = FieldName.create(var_names.getValue(i));

			boolean categorical = (var_type.getValue(i) > 0d);

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

	private MiningModel encodeMiningModel(RStringVector distribution, Segmentation segmentation, Double initF){

		switch(distribution.asScalar()){
			case "adaboost":
				return encodeClassification(segmentation, initF, -2d);
			case "bernoulli":
				return encodeClassification(segmentation, initF, -1d);
			case "gaussian":
				return encodeRegression(segmentation, initF);
			default:
				break;
		}

		throw new IllegalArgumentException();
	}

	private MiningModel encodeClassification(Segmentation segmentation, Double initF, double coefficient){
		DataField dataField = this.dataFields.get(0);

		FieldName targetField = dataField.getName();

		List<FieldName> activeFields = PMMLUtil.getNames(this.dataFields.subList(1, this.dataFields.size()));

		MiningSchema miningSchema = ModelUtil.createMiningSchema(null, activeFields);

		OutputField rawGbmValue = ModelUtil.createPredictedField(FieldName.create("rawGbmValue"));

		OutputField scaledGbmValue = new OutputField(FieldName.create("scaledGbmValue"))
			.setFeature(FeatureType.TRANSFORMED_VALUE)
			.setDataType(DataType.DOUBLE)
			.setOpType(OpType.CONTINUOUS)
			.setExpression(PMMLUtil.createApply("+", new FieldRef(rawGbmValue.getName()), PMMLUtil.createConstant(initF)));

		Output output = new Output()
			.addOutputFields(rawGbmValue, scaledGbmValue);

		FieldName inputField = scaledGbmValue.getName();

		MiningModel miningModel = new MiningModel(MiningFunctionType.REGRESSION, miningSchema)
			.setSegmentation(segmentation)
			.setOutput(output);

		return MiningModelUtil.createBinaryLogisticClassification(targetField, Lists.reverse(GBMConverter.BINARY_CLASSES), activeFields, miningModel, inputField, coefficient, true);
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

	private Predicate encodeIsMissingSplit(DataField dataField){
		SimplePredicate simplePredicate = new SimplePredicate()
			.setField(dataField.getName())
			.setOperator(SimplePredicate.Operator.IS_MISSING);

		return simplePredicate;
	}

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

	private Predicate encodeContinuousSplit(DataField dataField, Double split, boolean left){
		SimplePredicate simplePredicate = new SimplePredicate()
			.setField(dataField.getName())
			.setOperator(left ? SimplePredicate.Operator.LESS_THAN : SimplePredicate.Operator.GREATER_OR_EQUAL)
			.setValue(ValueUtil.formatValue(split));

		return simplePredicate;
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

	private static final List<String> BINARY_CLASSES = Arrays.asList("0", "1");
}
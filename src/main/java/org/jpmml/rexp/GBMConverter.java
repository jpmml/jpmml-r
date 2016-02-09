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
import java.util.List;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.dmg.pmml.Constant;
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
import org.dmg.pmml.Segment;
import org.dmg.pmml.Segmentation;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.Target;
import org.dmg.pmml.Targets;
import org.dmg.pmml.TreeModel;
import org.dmg.pmml.TreeModel.SplitCharacteristic;
import org.dmg.pmml.True;
import org.dmg.pmml.Value;
import org.jpmml.converter.ElementKey;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.ValueUtil;
import org.jpmml.model.visitors.FieldReferenceFinder;

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
	public PMML convert(RExp gbm){
		RExp initF = RExpUtil.field(gbm, "initF");
		RExp trees = RExpUtil.field(gbm, "trees");
		RExp c_splits = RExpUtil.field(gbm, "c.splits");
		RExp distribution = RExpUtil.field(gbm, "distribution");
		RExp response_name = RExpUtil.field(gbm, "response.name");
		RExp var_levels = RExpUtil.field(gbm, "var.levels");
		RExp var_names = RExpUtil.field(gbm, "var.names");
		RExp var_type = RExpUtil.field(gbm, "var.type");

		initFields(response_name, var_names, var_type, var_levels);

		List<Segment> segments = new ArrayList<>();

		for(int i = 0; i < trees.getRexpValueCount(); i++){
			RExp tree = trees.getRexpValue(i);

			TreeModel treeModel = encodeTreeModel(MiningFunctionType.REGRESSION, tree, c_splits);

			Segment segment = new Segment()
				.setId(String.valueOf(i + 1))
				.setPredicate(new True())
				.setModel(treeModel);

			segments.add(segment);
		}

		Segmentation segmentation = new Segmentation(MultipleModelMethodType.SUM, segments);

		MiningSchema miningSchema = PMMLUtil.createMiningSchema(this.dataFields);

		Output output = encodeOutput(distribution);

		DataField dataField = this.dataFields.get(0);

		Target target = new Target()
			.setField(dataField.getName())
			.setRescaleConstant(RExpUtil.asDouble(initF.getRealValue(0)));

		Targets targets = new Targets()
			.addTargets(target);

		MiningModel miningModel = new MiningModel(MiningFunctionType.REGRESSION, miningSchema)
			.setSegmentation(segmentation)
			.setOutput(output)
			.setTargets(targets);

		DataDictionary dataDictionary = new DataDictionary(this.dataFields);

		PMML pmml = new PMML("4.2", PMMLUtil.createHeader(Converter.NAME), dataDictionary)
			.addModels(miningModel);

		return pmml;
	}

	private void initFields(RExp response_name, RExp var_names, RExp var_type, RExp var_levels){

		// Dependent variable
		{
			RString name = response_name.getStringValue(0);

			DataField dataField = PMMLUtil.createDataField(FieldName.create(name.getStrval()), DataType.DOUBLE);

			this.dataFields.add(dataField);
		}

		// Independent variables
		for(int i = 0; i < var_names.getStringValueCount(); i++){
			RString var_name = var_names.getStringValue(i);

			boolean categorical = (var_type.getRealValue(i) > 0d);

			DataField dataField = PMMLUtil.createDataField(FieldName.create(var_name.getStrval()), categorical);

			if(categorical){
				RExp var_level = var_levels.getRexpValue(i);

				List<Value> values = dataField.getValues();
				values.addAll(PMMLUtil.createValues(RExpUtil.getStringList(var_level)));

				dataField = PMMLUtil.refineDataField(dataField);
			}

			this.dataFields.add(dataField);
		}
	}

	private TreeModel encodeTreeModel(MiningFunctionType miningFunction, RExp tree, RExp c_splits){
		Node root = new Node()
			.setId("1")
			.setPredicate(new True());

		encodeNode(root, 0, tree, c_splits);

		FieldReferenceFinder fieldReferenceFinder = new FieldReferenceFinder();
		fieldReferenceFinder.applyTo(root);

		MiningSchema miningSchema = PMMLUtil.createMiningSchema(fieldReferenceFinder);

		TreeModel treeModel = new TreeModel(miningFunction, miningSchema, root)
			.setSplitCharacteristic(SplitCharacteristic.MULTI_SPLIT);

		return treeModel;
	}

	private void encodeNode(Node node, int i, RExp tree, RExp c_splits){
		RExp splitVar = tree.getRexpValue(0);
		RExp splitCodePred = tree.getRexpValue(1);
		RExp leftNode = tree.getRexpValue(2);
		RExp rightNode = tree.getRexpValue(3);
		RExp missingNode = tree.getRexpValue(4);
		RExp prediction = tree.getRexpValue(7);

		Predicate missingPredicate = null;

		Predicate leftPredicate = null;
		Predicate rightPredicate = null;

		Integer var = splitVar.getIntValue(i);
		if(var != -1){
			DataField dataField = this.dataFields.get(var + 1);

			missingPredicate = encodeIsMissingSplit(dataField);

			Double split = splitCodePred.getRealValue(i);

			OpType opType = dataField.getOpType();
			switch(opType){
				case CATEGORICAL:
					Integer index = RExpUtil.asInteger(split);

					RExp c_split = c_splits.getRexpValue(index);

					List<Integer> splitValues = c_split.getIntValueList();

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
			Double value = prediction.getRealValue(i);

			node.setScore(ValueUtil.formatValue(value));
		}

		Integer missing = missingNode.getIntValue(i);
		if(missing != -1){
			Node missingChild = new Node()
				.setId(String.valueOf(missing + 1))
				.setPredicate(missingPredicate);

			encodeNode(missingChild, missing, tree, c_splits);

			node.addNodes(missingChild);
		}

		Integer left = leftNode.getIntValue(i);
		if(left != -1){
			Node leftChild = new Node()
				.setId(String.valueOf(left + 1))
				.setPredicate(leftPredicate);

			encodeNode(leftChild, left, tree, c_splits);

			node.addNodes(leftChild);
		}

		Integer right = rightNode.getIntValue(i);
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

	private Output encodeOutput(RExp distribution){
		RExp name = RExpUtil.field(distribution, "name");

		RString distributionName = name.getStringValue(0);

		if("adaboost".equals(distributionName.getStrval())){
			return encodeAdaBoostOutput();
		} else

		if("bernoulli".equals(distributionName.getStrval())){
			return encodeBernoulliOutput();
		}

		return null;
	}

	private Output encodeAdaBoostOutput(){
		return encodeBinaryClassificationOutput(FieldName.create("adaBoostValue"), PMMLUtil.createConstant(-2d));
	}

	private Output encodeBernoulliOutput(){
		return encodeBinaryClassificationOutput(FieldName.create("bernoulliValue"), PMMLUtil.createConstant(-1d));
	}

	private Output encodeBinaryClassificationOutput(FieldName name, Constant multiplier){
		Constant one = PMMLUtil.createConstant(1d);

		OutputField gbmValue = new OutputField(name)
			.setFeature(FeatureType.PREDICTED_VALUE);

		// "p(1) = 1 / (1 + exp(multiplier * y))"
		OutputField probabilityOne = new OutputField(FieldName.create("probability_1"))
			.setFeature(FeatureType.TRANSFORMED_VALUE)
			.setDataType(DataType.DOUBLE)
			.setOpType(OpType.CONTINUOUS)
			.setExpression(PMMLUtil.createApply("/", one, PMMLUtil.createApply("+", one, PMMLUtil.createApply("exp", PMMLUtil.createApply("*", multiplier, new FieldRef(gbmValue.getName()))))));

		// "p(0) = 1 - p(1)"
		OutputField probabilityZero = new OutputField(FieldName.create("probability_0"))
			.setFeature(FeatureType.TRANSFORMED_VALUE)
			.setDataType(DataType.DOUBLE)
			.setOpType(OpType.CONTINUOUS)
			.setExpression(PMMLUtil.createApply("-", one, new FieldRef(probabilityOne.getName())));

		Output output = new Output()
			.addOutputFields(gbmValue, probabilityOne, probabilityZero);

		return output;
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
}
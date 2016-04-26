/*
 * Copyright (c) 2014 Villu Ruusmann
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
import com.google.common.math.DoubleMath;
import com.google.common.primitives.UnsignedLong;
import org.dmg.pmml.DataDictionary;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunctionType;
import org.dmg.pmml.MiningModel;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.MultipleModelMethodType;
import org.dmg.pmml.Node;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Output;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.Segmentation;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.TreeModel;
import org.dmg.pmml.True;
import org.dmg.pmml.Value;
import org.jpmml.converter.ElementKey;
import org.jpmml.converter.FieldTypeAnalyzer;
import org.jpmml.converter.MiningModelUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.ValueUtil;

public class RandomForestConverter extends Converter {

	private List<DataField> dataFields = new ArrayList<>();

	private List<DataField> treeDataFields = null;

	private LoadingCache<ElementKey, Predicate> predicateCache = CacheBuilder.newBuilder()
		.build(new CacheLoader<ElementKey, Predicate>(){

			@Override
			public Predicate load(ElementKey key){
				Object[] content = key.getContent();

				return encodeCategoricalSplit((DataField)content[0], (Double)content[1], (Boolean)content[2]);
			}
		});


	public RandomForestConverter(){
	}

	@Override
	public PMML convert(RExp rexp){
		return convert((RGenericVector)rexp);
	}

	private PMML convert(RGenericVector randomForest){
		RStringVector type = (RStringVector)randomForest.getValue("type");
		RGenericVector forest = (RGenericVector)randomForest.getValue("forest");

		RNumberVector<?> y;

		try {
			y = (RNumberVector<?>)randomForest.getValue("y");
		} catch(IllegalArgumentException iae){
			y = null;
		} // End try

		try {
			RExp terms = randomForest.getValue("terms");

			// The RF model was trained using the formula interface
			initFormulaFields(terms);
		} catch(IllegalArgumentException iae){
			RStringVector xNames;

			try {
				xNames = (RStringVector)randomForest.getValue("xNames");
			} catch(IllegalArgumentException iaeChild){
				RExp xlevels = forest.getValue("xlevels");

				xNames = xlevels.names();
			}

			RNumberVector<?> ncat = (RNumberVector<?>)forest.getValue("ncat");

			// The RF model was trained using the matrix (ie. non-formula) interface
			initNonFormulaFields(xNames, ncat, y);
		}

		switch(type.asScalar()){
			case "regression":
				return convertRegression(forest);
			case "classification":
				return convertClassification(forest, (RIntegerVector)y);
			default:
				break;
		}

		throw new IllegalArgumentException();
	}

	private PMML convertRegression(RGenericVector forest){
		RNumberVector<?> leftDaughter = (RNumberVector<?>)forest.getValue("leftDaughter");
		RNumberVector<?> rightDaughter = (RNumberVector<?>)forest.getValue("rightDaughter");
		RDoubleVector nodepred = (RDoubleVector)forest.getValue("nodepred");
		RNumberVector<?> bestvar = (RNumberVector<?>)forest.getValue("bestvar");
		RDoubleVector xbestsplit = (RDoubleVector)forest.getValue("xbestsplit");
		RNumberVector<?> ncat = (RNumberVector<?>)forest.getValue("ncat");
		RIntegerVector nrnodes = (RIntegerVector)forest.getValue("nrnodes");
		RDoubleVector ntree = (RDoubleVector)forest.getValue("ntree");
		RGenericVector xlevels = (RGenericVector)forest.getValue("xlevels");

		initActiveFields(xlevels, ncat);

		ScoreEncoder<Double> scoreEncoder = new ScoreEncoder<Double>(){

			@Override
			public String encode(Double key){
				return ValueUtil.formatValue(key);
			}
		};

		int rows = nrnodes.asScalar();
		int columns = ValueUtil.asInt(ntree.asScalar());

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < columns; i++){
			TreeModel treeModel = encodeTreeModel(
					MiningFunctionType.REGRESSION,
					RExpUtil.getColumn(leftDaughter.getValues(), rows, columns, i),
					RExpUtil.getColumn(rightDaughter.getValues(), rows, columns, i),
					scoreEncoder,
					RExpUtil.getColumn(nodepred.getValues(), rows, columns, i),
					RExpUtil.getColumn(bestvar.getValues(), rows, columns, i),
					RExpUtil.getColumn(xbestsplit.getValues(), rows, columns, i)
				);

			treeModels.add(treeModel);
		}

		return encodePMML(MiningFunctionType.REGRESSION, treeModels);
	}

	private PMML convertClassification(RGenericVector forest, RIntegerVector y){
		RNumberVector<?> bestvar = (RNumberVector<?>)forest.getValue("bestvar");
		RNumberVector<?> treemap = (RNumberVector<?>)forest.getValue("treemap");
		RIntegerVector nodepred = (RIntegerVector)forest.getValue("nodepred");
		RDoubleVector xbestsplit = (RDoubleVector)forest.getValue("xbestsplit");
		RNumberVector<?> ncat = (RNumberVector<?>)forest.getValue("ncat");
		RIntegerVector nrnodes = (RIntegerVector)forest.getValue("nrnodes");
		RDoubleVector ntree = (RDoubleVector)forest.getValue("ntree");
		RGenericVector xlevels = (RGenericVector)forest.getValue("xlevels");

		initPredictedFields(y);
		initActiveFields(xlevels, ncat);

		ScoreEncoder<Integer> scoreEncoder = new ScoreEncoder<Integer>(){

			@Override
			public String encode(Integer key){
				Value value = getLevel(key - 1);

				return value.getValue();
			}
		};

		int rows = nrnodes.asScalar();
		int columns = ValueUtil.asInt(ntree.asScalar());

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < columns; i++){
			List<? extends Number> daughters = RExpUtil.getColumn(treemap.getValues(), 2 * rows, columns, i);

			TreeModel treeModel = encodeTreeModel(
					MiningFunctionType.CLASSIFICATION,
					RExpUtil.getColumn(daughters, rows, columns, 0),
					RExpUtil.getColumn(daughters, rows, columns, 1),
					scoreEncoder,
					RExpUtil.getColumn(nodepred.getValues(), rows, columns, i),
					RExpUtil.getColumn(bestvar.getValues(), rows, columns, i),
					RExpUtil.getColumn(xbestsplit.getValues(), rows, columns, i)
				);

			treeModels.add(treeModel);
		}

		return encodePMML(MiningFunctionType.CLASSIFICATION, treeModels);
	}

	private PMML encodePMML(MiningFunctionType miningFunction, List<TreeModel> treeModels){
		MultipleModelMethodType multipleModelMethod;

		switch(miningFunction){
			case REGRESSION:
				multipleModelMethod = MultipleModelMethodType.AVERAGE;
				break;
			case CLASSIFICATION:
				multipleModelMethod = MultipleModelMethodType.MAJORITY_VOTE;
				break;
			default:
				throw new IllegalArgumentException();
		}

		Segmentation segmentation = MiningModelUtil.createSegmentation(multipleModelMethod, treeModels);

		FieldTypeAnalyzer fieldTypeAnalyzer = new RandomForestFieldTypeAnalyzer();
		fieldTypeAnalyzer.applyTo(segmentation);

		PMMLUtil.refineDataFields(this.dataFields, fieldTypeAnalyzer);

		MiningSchema miningSchema = ModelUtil.createMiningSchema(this.dataFields);

		Output output = encodeOutput(miningFunction);

		MiningModel miningModel = new MiningModel(miningFunction, miningSchema)
			.setSegmentation(segmentation)
			.setOutput(output);

		DataDictionary dataDictionary = new DataDictionary(this.dataFields);

		PMML pmml = new PMML("4.2", createHeader(), dataDictionary)
			.addModels(miningModel);

		return pmml;
	}

	private void initFormulaFields(RExp terms){
		RStringVector dataClasses = (RStringVector)terms.getAttributeValue("dataClasses");

		RStringVector names = dataClasses.names();

		for(int i = 0; i < names.size(); i++){
			String name = names.getValue(i);

			String dataClass = dataClasses.getValue(i);

			DataField dataField = PMMLUtil.createDataField(FieldName.create(name), RExpUtil.getDataType(dataClass));

			this.dataFields.add(dataField);
		}
	}

	private void initNonFormulaFields(RStringVector xNames, RNumberVector<?> ncat, RNumberVector<?> y){

		// Dependent variable
		{
			boolean categorical = (y instanceof RIntegerVector);

			DataField dataField = PMMLUtil.createDataField(FieldName.create("_target"), categorical);

			this.dataFields.add(dataField);
		}

		// Independent variable(s)
		for(int i = 0; i < xNames.size(); i++){
			String xName = xNames.getValue(i);

			boolean categorical = ((ncat.getValue(i)).doubleValue() > 1d);

			DataField dataField = PMMLUtil.createDataField(FieldName.create(xName), categorical);

			this.dataFields.add(dataField);
		}
	}

	private void initActiveFields(RGenericVector xlevels, RNumberVector<?> ncat){
		RStringVector names;

		try {
			names = xlevels.names();
		} catch(IllegalArgumentException iae){
			names = null;
		}

		this.treeDataFields = new ArrayList<>();

		for(int i = 0; i < ncat.size(); i++){
			DataField dataField;

			if(names != null){
				dataField = PMMLUtil.getField(FieldName.create(names.getValue(i)), this.dataFields);
			} else

			{
				dataField = this.dataFields.get(i + 1);
			}

			this.treeDataFields.add(dataField);

			boolean categorical = ((ncat.getValue(i)).doubleValue() > 1d);
			if(!categorical){
				continue;
			}

			RStringVector xvalues = (RStringVector)xlevels.getValue(i);

			List<Value> values = dataField.getValues();
			values.addAll(PMMLUtil.createValues(xvalues.getValues()));

			dataField = PMMLUtil.refineDataField(dataField);
		}
	}

	private void initPredictedFields(RIntegerVector y){
		DataField dataField = this.dataFields.get(0);

		RStringVector levels = y.getFactorLevels();

		List<Value> values = dataField.getValues();
		values.addAll(PMMLUtil.createValues(levels.getValues()));

		dataField = PMMLUtil.refineDataField(dataField);
	}

	private <P extends Number> TreeModel encodeTreeModel(MiningFunctionType miningFunction, List<? extends Number> leftDaughter, List<? extends Number> rightDaughter, ScoreEncoder<P> scoreEncoder, List<P> nodepred, List<? extends Number> bestvar, List<Double> xbestsplit){
		Node root = new Node()
			.setId("1")
			.setPredicate(new True());

		encodeNode(root, 0, leftDaughter, rightDaughter, bestvar, xbestsplit, scoreEncoder, nodepred);

		MiningSchema miningSchema = ModelUtil.createMiningSchema(null, this.dataFields.subList(1, this.dataFields.size()), root);

		TreeModel treeModel = new TreeModel(miningFunction, miningSchema, root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	private <P extends Number> void encodeNode(Node node, int i, List<? extends Number> leftDaughter, List<? extends Number> rightDaughter, List<? extends Number> bestvar, List<Double> xbestsplit, ScoreEncoder<P> scoreEncoder, List<P> nodepred){
		Predicate leftPredicate = null;
		Predicate rightPredicate = null;

		int var = ValueUtil.asInt(bestvar.get(i));
		if(var != 0){
			DataField dataField = this.treeDataFields.get(var - 1);

			Double split = xbestsplit.get(i);

			OpType opType = dataField.getOpType();

			DataType dataType = dataField.getDataType();
			switch(dataType){
				case BOOLEAN:
					opType = OpType.CONTINUOUS;
					break;
				default:
					break;
			}

			switch(opType){
				case CATEGORICAL:
					leftPredicate = this.predicateCache.getUnchecked(new ElementKey(dataField, split, Boolean.TRUE));
					rightPredicate = this.predicateCache.getUnchecked(new ElementKey(dataField, split, Boolean.FALSE));
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
			P prediction = nodepred.get(i);

			node.setScore(scoreEncoder.encode(prediction));
		}

		int left = ValueUtil.asInt(leftDaughter.get(i));
		if(left != 0){
			Node leftChild = new Node()
				.setId(String.valueOf(left))
				.setPredicate(leftPredicate);

			encodeNode(leftChild, left - 1, leftDaughter, rightDaughter, bestvar, xbestsplit, scoreEncoder, nodepred);

			node.addNodes(leftChild);
		}

		int right = ValueUtil.asInt(rightDaughter.get(i));
		if(right != 0){
			Node rightChild = new Node()
				.setId(String.valueOf(right))
				.setPredicate(rightPredicate);

			encodeNode(rightChild, right - 1, leftDaughter, rightDaughter, bestvar, xbestsplit, scoreEncoder, nodepred);

			node.addNodes(rightChild);
		}
	}

	private Predicate encodeCategoricalSplit(DataField dataField, Double split, boolean left){
		List<Value> values = selectValues(dataField.getValues(), split, left);

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
		SimplePredicate simplePredicate;

		DataType dataType = dataField.getDataType();

		if((DataType.DOUBLE).equals(dataType)){
			simplePredicate = new SimplePredicate()
				.setField(dataField.getName())
				.setOperator(left ? SimplePredicate.Operator.LESS_OR_EQUAL : SimplePredicate.Operator.GREATER_THAN)
				.setValue(ValueUtil.formatValue(split));
		} else

		if((DataType.BOOLEAN).equals(dataType)){
			simplePredicate = new SimplePredicate()
				.setField(dataField.getName())
				.setOperator(SimplePredicate.Operator.EQUAL)
				.setValue(split.doubleValue() <= 0.5d ? Boolean.toString(!left) : Boolean.toString(left));
		} else

		{
			throw new IllegalArgumentException();
		}

		return simplePredicate;
	}

	private Output encodeOutput(MiningFunctionType miningFunction){

		switch(miningFunction){
			case CLASSIFICATION:
				return encodeClassificationOutput();
			default:
				return null;
		}
	}

	private Output encodeClassificationOutput(){
		DataField dataField = this.dataFields.get(0);

		Output output = new Output(ModelUtil.createProbabilityFields(dataField));

		return output;
	}

	private Value getLevel(int i){
		DataField dataField = this.dataFields.get(0);

		List<Value> values = dataField.getValues();

		return values.get(i);
	}

	static
	List<Value> selectValues(List<Value> values, Double split, boolean left){
		List<Value> result = new ArrayList<>();

		UnsignedLong bits = toUnsignedLong(split.doubleValue());

		for(int i = 0; i < values.size(); i++){
			Value value = values.get(i);

			boolean append;

			// Send "true" categories to the left
			if(left){
				// Test if the least significant bit (LSB) is 1
				append = (bits.mod(RandomForestConverter.TWO)).equals(UnsignedLong.ONE);
			} else

			// Send all other categories to the right
			{
				// Test if the LSB is 0
				append = (bits.mod(RandomForestConverter.TWO)).equals(UnsignedLong.ZERO);
			} // End if

			if(append){
				result.add(value);
			}

			bits = bits.dividedBy(RandomForestConverter.TWO);
		}

		return result;
	}

	static
	UnsignedLong toUnsignedLong(double value){

		if(!DoubleMath.isMathematicalInteger(value)){
			throw new IllegalArgumentException();
		}

		return UnsignedLong.fromLongBits((long)value);
	}

	static
	private interface ScoreEncoder<K extends Number> {

		String encode(K key);
	}

	private static final UnsignedLong TWO = UnsignedLong.valueOf(2L);
}
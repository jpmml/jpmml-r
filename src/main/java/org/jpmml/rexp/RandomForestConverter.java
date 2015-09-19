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

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
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
import org.dmg.pmml.Segment;
import org.dmg.pmml.Segmentation;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.TreeModel;
import org.dmg.pmml.True;
import org.dmg.pmml.Value;
import org.jpmml.converter.ElementKey;
import org.jpmml.converter.FieldCollector;
import org.jpmml.converter.FieldTypeAnalyzer;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.TreeModelFieldCollector;

public class RandomForestConverter extends Converter {

	private List<DataField> dataFields = new ArrayList<>();

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
	public PMML convert(RExp randomForest){
		RExp type = RExpUtil.field(randomForest, "type");
		RExp forest = RExpUtil.field(randomForest, "forest");

		try {
			RExp terms = RExpUtil.field(randomForest, "terms");

			// The RF model was trained using the formula interface
			initFormulaFields(terms);
		} catch(IllegalArgumentException iae){
			RExp xlevels = RExpUtil.field(forest, "xlevels");

			RExp xNames;

			try {
				xNames = RExpUtil.field(randomForest, "xNames");
			} catch(IllegalArgumentException iaeChild){
				xNames = RExpUtil.attribute(xlevels, "names");
			}

			RExp ncat = RExpUtil.field(forest, "ncat");

			RExp y;

			try {
				y = RExpUtil.field(randomForest, "y");
			} catch(IllegalArgumentException iaeChild){
				y = null;
			}

			// The RF model was trained using the matrix (ie. non-formula) interface
			initNonFormulaFields(xNames, ncat, y);
		}

		PMML pmml;

		RString typeValue = type.getStringValue(0);

		if("regression".equals(typeValue.getStrval())){
			pmml = convertRegression(forest);
		} else

		if("classification".equals(typeValue.getStrval())){
			RExp y = RExpUtil.field(randomForest, "y");

			pmml = convertClassification(forest, y);
		} else

		{
			throw new IllegalArgumentException();
		}

		return pmml;
	}

	private PMML convertRegression(RExp forest){
		RExp leftDaughter = RExpUtil.field(forest, "leftDaughter");
		RExp rightDaughter = RExpUtil.field(forest, "rightDaughter");
		RExp nodepred = RExpUtil.field(forest, "nodepred");
		RExp bestvar = RExpUtil.field(forest, "bestvar");
		RExp xbestsplit = RExpUtil.field(forest, "xbestsplit");
		RExp ncat = RExpUtil.field(forest, "ncat");
		RExp nrnodes = RExpUtil.field(forest, "nrnodes");
		RExp ntree = RExpUtil.field(forest, "ntree");
		RExp xlevels = RExpUtil.field(forest, "xlevels");

		initActiveFields(xlevels, ncat);

		ScoreEncoder<Double> scoreEncoder = new ScoreEncoder<Double>(){

			@Override
			public String encode(Double key){
				return PMMLUtil.formatValue(key);
			}
		};

		List<Integer> leftDaughterIndices = getIndices(leftDaughter);
		List<Integer> rightDaughterIndices = getIndices(rightDaughter);
		List<Integer> bestvarIndices = getIndices(bestvar);

		int rows = nrnodes.getIntValue(0);
		int columns = (int)ntree.getRealValue(0);

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < columns; i++){
			TreeModel treeModel = encodeTreeModel(
					MiningFunctionType.REGRESSION,
					RExpUtil.getColumn(leftDaughterIndices, i, rows, columns),
					RExpUtil.getColumn(rightDaughterIndices, i, rows, columns),
					scoreEncoder,
					RExpUtil.getColumn(nodepred.getRealValueList(), i, rows, columns),
					RExpUtil.getColumn(bestvarIndices, i, rows, columns),
					RExpUtil.getColumn(xbestsplit.getRealValueList(), i, rows, columns)
				);

			treeModels.add(treeModel);
		}

		return encodePMML(MiningFunctionType.REGRESSION, treeModels);
	}

	private PMML convertClassification(RExp forest, RExp y){
		RExp bestvar = RExpUtil.field(forest, "bestvar");
		RExp treemap = RExpUtil.field(forest, "treemap");
		RExp nodepred = RExpUtil.field(forest, "nodepred");
		RExp xbestsplit = RExpUtil.field(forest, "xbestsplit");
		RExp ncat = RExpUtil.field(forest, "ncat");
		RExp nrnodes = RExpUtil.field(forest, "nrnodes");
		RExp ntree = RExpUtil.field(forest, "ntree");
		RExp xlevels = RExpUtil.field(forest, "xlevels");

		initPredictedFields(y);
		initActiveFields(xlevels, ncat);

		ScoreEncoder<Integer> scoreEncoder = new ScoreEncoder<Integer>(){

			@Override
			public String encode(Integer key){
				Value value = getLevel(key.intValue() - 1);

				return value.getValue();
			}
		};

		List<Integer> treemapIndices = getIndices(treemap);
		List<Integer> nodepredIndices = getIndices(nodepred);
		List<Integer> bestvarIndices = getIndices(bestvar);

		int rows = nrnodes.getIntValue(0);
		int columns = (int)ntree.getRealValue(0);

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < columns; i++){
			List<Integer> daughters = RExpUtil.getColumn(treemapIndices, i, 2 * rows, columns);

			TreeModel treeModel = encodeTreeModel(
					MiningFunctionType.CLASSIFICATION,
					RExpUtil.getColumn(daughters, 0, rows, columns),
					RExpUtil.getColumn(daughters, 1, rows, columns),
					scoreEncoder,
					RExpUtil.getColumn(nodepredIndices, i, rows, columns),
					RExpUtil.getColumn(bestvarIndices, i, rows, columns),
					RExpUtil.getColumn(xbestsplit.getRealValueList(), i, rows, columns)
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

		List<Segment> segments = new ArrayList<>();

		for(int i = 0; i < treeModels.size(); i++){
			TreeModel treeModel = treeModels.get(i);

			Segment segment = new Segment()
				.setId(String.valueOf(i + 1))
				.setPredicate(new True())
				.setModel(treeModel);

			segments.add(segment);
		}

		Segmentation segmentation = new Segmentation(multipleModelMethod, segments);

		FieldTypeAnalyzer fieldTypeAnalyzer = new RandomForestFieldTypeAnalyzer();
		fieldTypeAnalyzer.applyTo(segmentation);

		PMMLUtil.refineDataFields(this.dataFields, fieldTypeAnalyzer);

		MiningSchema miningSchema = PMMLUtil.createMiningSchema(this.dataFields);

		Output output = encodeOutput(miningFunction);

		MiningModel miningModel = new MiningModel(miningFunction, miningSchema)
			.setSegmentation(segmentation)
			.setOutput(output);

		DataDictionary dataDictionary = new DataDictionary(this.dataFields);

		PMML pmml = new PMML("4.2", PMMLUtil.createHeader(Converter.NAME), dataDictionary)
			.addModels(miningModel);

		return pmml;
	}

	private void initFormulaFields(RExp terms){
		RExp dataClasses = RExpUtil.attribute(terms, "dataClasses");

		RExp names = RExpUtil.attribute(dataClasses, "names");

		for(int i = 0; i < names.getStringValueCount(); i++){
			RString name = names.getStringValue(i);

			RString dataClass = dataClasses.getStringValue(i);

			DataField dataField = PMMLUtil.createDataField(FieldName.create(name.getStrval()), RExpUtil.getDataType(dataClass.getStrval()));

			this.dataFields.add(dataField);
		}
	}

	private void initNonFormulaFields(RExp xNames, RExp ncat, RExp y){

		// Dependent variable
		{
			boolean categorical = (y != null && y.getStringValueCount() > 0);

			DataField dataField = PMMLUtil.createDataField(FieldName.create("_target"), categorical);

			this.dataFields.add(dataField);
		}

		// Independent variable(s)
		for(int i = 0; i < xNames.getStringValueCount(); i++){
			RString xName = xNames.getStringValue(i);

			boolean categorical;

			if(ncat.getIntValueCount() > 0){
				categorical = (ncat.getIntValue(i) > 1);
			} else

			if(ncat.getRealValueCount() > 0){
				categorical = (ncat.getRealValue(i) > 1d);
			} else

			{
				throw new IllegalArgumentException();
			}

			DataField dataField = PMMLUtil.createDataField(FieldName.create(xName.getStrval()), categorical);

			this.dataFields.add(dataField);
		}
	}

	private void initActiveFields(RExp xlevels, RExp ncat){

		for(int i = 0; i < ncat.getIntValueCount(); i++){
			DataField dataField = this.dataFields.get(i + 1);

			boolean categorical = (ncat.getIntValue(i) > 1);
			if(!categorical){
				continue;
			}

			RExp xvalues = xlevels.getRexpValue(i);

			List<Value> values = dataField.getValues();
			values.addAll(PMMLUtil.createValues(RExpUtil.getStringList(xvalues)));

			dataField = PMMLUtil.refineDataField(dataField);
		}
	}

	private void initPredictedFields(RExp y){
		DataField dataField = this.dataFields.get(0);

		RExp levels = RExpUtil.attribute(y, "levels");

		List<Value> values = dataField.getValues();
		values.addAll(PMMLUtil.createValues(RExpUtil.getStringList(levels)));

		dataField = PMMLUtil.refineDataField(dataField);
	}

	private <P extends Number> TreeModel encodeTreeModel(MiningFunctionType miningFunction, List<Integer> leftDaughter, List<Integer> rightDaughter, ScoreEncoder<P> scoreEncoder, List<P> nodepred, List<Integer> bestvar, List<Double> xbestsplit){
		Node root = new Node()
			.setId("1")
			.setPredicate(new True());

		encodeNode(root, 0, leftDaughter, rightDaughter, bestvar, xbestsplit, scoreEncoder, nodepred);

		FieldCollector fieldCollector = new TreeModelFieldCollector();
		fieldCollector.applyTo(root);

		MiningSchema miningSchema = PMMLUtil.createMiningSchema(fieldCollector);

		TreeModel treeModel = new TreeModel(miningFunction, miningSchema, root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	private <P extends Number> void encodeNode(Node node, int i, List<Integer> leftDaughter, List<Integer> rightDaughter, List<Integer> bestvar, List<Double> xbestsplit, ScoreEncoder<P> scoreEncoder, List<P> nodepred){
		Predicate leftPredicate = null;
		Predicate rightPredicate = null;

		Integer var = bestvar.get(i);
		if(var != 0){
			DataField dataField = this.dataFields.get(var);

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

		Integer left = leftDaughter.get(i);
		if(left != 0){
			Node leftChild = new Node()
				.setId(String.valueOf(left))
				.setPredicate(leftPredicate);

			encodeNode(leftChild, left - 1, leftDaughter, rightDaughter, bestvar, xbestsplit, scoreEncoder, nodepred);

			node.addNodes(leftChild);
		}

		Integer right = rightDaughter.get(i);
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
				.setValue(PMMLUtil.formatValue(split));
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

		Output output = new Output(PMMLUtil.createProbabilityFields(dataField));

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
	private List<Integer> getIndices(RExp rexp){
		List<Integer> intValues = rexp.getIntValueList();
		if(intValues.size() > 0){
			return intValues;
		}

		List<Double> realValues = rexp.getRealValueList();
		if(realValues.size() > 0){
			Function<Number, Integer> function = new Function<Number, Integer>(){

				@Override
				public Integer apply(Number number){
					return RExpUtil.asInteger(number);
				}
			};

			return Lists.transform(realValues, function);
		}

		throw new IllegalArgumentException();
	}

	static
	private interface ScoreEncoder<K extends Number> {

		String encode(K key);
	}

	private static final UnsignedLong TWO = UnsignedLong.valueOf(2L);
}
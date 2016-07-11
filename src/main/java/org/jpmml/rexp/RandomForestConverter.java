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

import com.google.common.math.DoubleMath;
import com.google.common.primitives.UnsignedLong;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunctionType;
import org.dmg.pmml.MiningModel;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.MultipleModelMethodType;
import org.dmg.pmml.Node;
import org.dmg.pmml.Output;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.Segmentation;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.TreeModel;
import org.dmg.pmml.True;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ListFeature;
import org.jpmml.converter.MiningModelUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;

public class RandomForestConverter extends ModelConverter<RGenericVector> {

	@Override
	public void encodeFeatures(RGenericVector randomForest, FeatureMapper featureMapper){
		RGenericVector forest = (RGenericVector)randomForest.getValue("forest");

		RNumberVector<?> y;

		try {
			y = (RNumberVector<?>)randomForest.getValue("y");
		} catch(IllegalArgumentException iae){
			y = null;
		}

		RNumberVector<?> ncat = (RNumberVector<?>)forest.getValue("ncat");
		RGenericVector xlevels = (RGenericVector)forest.getValue("xlevels");

		try {
			RExp terms = randomForest.getValue("terms");

			// The RF model was trained using the formula interface
			encodeFormula(terms, y, xlevels, ncat, featureMapper);
		} catch(IllegalArgumentException iae){
			RStringVector xNames;

			try {
				xNames = (RStringVector)randomForest.getValue("xNames");
			} catch(IllegalArgumentException iaeChild){
				xNames = xlevels.names();
			}

			// The RF model was trained using the matrix (ie. non-formula) interface
			encodeNonFormula(xNames, y, xlevels, ncat, featureMapper);
		}
	}

	@Override
	public Schema createSchema(FeatureMapper featureMapper){
		return featureMapper.createSupervisedSchema();
	}

	@Override
	public MiningModel encodeModel(RGenericVector randomForest, Schema schema){
		RStringVector type = (RStringVector)randomForest.getValue("type");
		RGenericVector forest = (RGenericVector)randomForest.getValue("forest");

		switch(type.asScalar()){
			case "regression":
				return encodeRegression(forest, schema);
			case "classification":
				return encodeClassification(forest, schema);
			default:
				throw new IllegalArgumentException();
		}
	}

	private void encodeFormula(RExp terms, RNumberVector<?> y, RGenericVector xlevels, RNumberVector<?> ncat, FeatureMapper featureMapper){
		RStringVector dataClasses = (RStringVector)terms.getAttributeValue("dataClasses");

		RStringVector dataClassNames = dataClasses.names();

		// Dependent variable
		{
			FieldName name = FieldName.create(dataClassNames.getValue(0));
			DataType dataType = RExpUtil.getDataType(dataClasses.getValue(0));

			List<String> targetCategories = getFactorLevels(y);
			if(targetCategories != null){
				featureMapper.append(name, dataType, targetCategories);
			} else

			{
				featureMapper.append(name, dataType);
			}
		}

		RStringVector xlevelNames = xlevels.names();

		// Independent variables
		for(int i = 0; i < ncat.size(); i++){
			int index = (dataClassNames.getValues()).indexOf(xlevelNames.getValue(i));
			if(index < 1){
				throw new IllegalArgumentException();
			}

			FieldName name = FieldName.create(dataClassNames.getValue(index));
			DataType dataType = RExpUtil.getDataType(dataClasses.getValue(index));

			boolean categorical = ((ncat.getValue(i)).doubleValue() > 1d);
			if(categorical){
				RStringVector levels = (RStringVector)xlevels.getValue(i);

				featureMapper.append(name, dataType, levels.getValues());
			} else

			{
				featureMapper.append(name, dataType);
			}
		}
	}

	private void encodeNonFormula(RStringVector xNames, RNumberVector<?> y, RGenericVector xlevels, RNumberVector<?> ncat, FeatureMapper featureMapper){

		// Dependent variable
		{
			FieldName name = FieldName.create("_target");

			List<String> targetCategories = getFactorLevels(y);
			if(targetCategories != null){
				featureMapper.append(name, targetCategories);
			} else

			{
				featureMapper.append(name, false);
			}
		}

		// Independernt variables
		for(int i = 0; i < ncat.size(); i++){
			FieldName name = FieldName.create(xNames.getValue(i));

			boolean categorical = ((ncat.getValue(i)).doubleValue() > 1d);
			if(categorical){
				RStringVector levels = (RStringVector)xlevels.getValue(i);

				featureMapper.append(name, levels.getValues());
			} else

			{
				featureMapper.append(name, false);
			}
		}
	}

	private MiningModel encodeRegression(RGenericVector forest, final Schema schema){
		RNumberVector<?> leftDaughter = (RNumberVector<?>)forest.getValue("leftDaughter");
		RNumberVector<?> rightDaughter = (RNumberVector<?>)forest.getValue("rightDaughter");
		RDoubleVector nodepred = (RDoubleVector)forest.getValue("nodepred");
		RNumberVector<?> bestvar = (RNumberVector<?>)forest.getValue("bestvar");
		RDoubleVector xbestsplit = (RDoubleVector)forest.getValue("xbestsplit");
		RIntegerVector nrnodes = (RIntegerVector)forest.getValue("nrnodes");
		RDoubleVector ntree = (RDoubleVector)forest.getValue("ntree");

		ScoreEncoder<Double> scoreEncoder = new ScoreEncoder<Double>(){

			@Override
			public String encode(Double key){
				return ValueUtil.formatValue(key);
			}
		};

		int rows = nrnodes.asScalar();
		int columns = ValueUtil.asInt(ntree.asScalar());

		Schema segmentSchema = schema.toAnonymousSchema();

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < columns; i++){
			TreeModel treeModel = encodeTreeModel(
					MiningFunctionType.REGRESSION,
					RExpUtil.getColumn(leftDaughter.getValues(), rows, columns, i),
					RExpUtil.getColumn(rightDaughter.getValues(), rows, columns, i),
					scoreEncoder,
					RExpUtil.getColumn(nodepred.getValues(), rows, columns, i),
					RExpUtil.getColumn(bestvar.getValues(), rows, columns, i),
					RExpUtil.getColumn(xbestsplit.getValues(), rows, columns, i),
					segmentSchema
				);

			treeModels.add(treeModel);
		}

		Segmentation segmentation = MiningModelUtil.createSegmentation(MultipleModelMethodType.AVERAGE, treeModels);

		MiningSchema miningSchema = ModelUtil.createMiningSchema(schema);

		MiningModel miningModel = new MiningModel(MiningFunctionType.REGRESSION, miningSchema)
			.setSegmentation(segmentation);

		return miningModel;
	}

	private MiningModel encodeClassification(RGenericVector forest, final Schema schema){
		RNumberVector<?> bestvar = (RNumberVector<?>)forest.getValue("bestvar");
		RNumberVector<?> treemap = (RNumberVector<?>)forest.getValue("treemap");
		RIntegerVector nodepred = (RIntegerVector)forest.getValue("nodepred");
		RDoubleVector xbestsplit = (RDoubleVector)forest.getValue("xbestsplit");
		RIntegerVector nrnodes = (RIntegerVector)forest.getValue("nrnodes");
		RDoubleVector ntree = (RDoubleVector)forest.getValue("ntree");

		ScoreEncoder<Integer> scoreEncoder = new ScoreEncoder<Integer>(){

			private List<String> targetCategories = schema.getTargetCategories();


			@Override
			public String encode(Integer key){
				return this.targetCategories.get(key - 1);
			}
		};

		int rows = nrnodes.asScalar();
		int columns = ValueUtil.asInt(ntree.asScalar());

		Schema segmentSchema = schema.toAnonymousSchema();

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
					RExpUtil.getColumn(xbestsplit.getValues(), rows, columns, i),
					segmentSchema
				);

			treeModels.add(treeModel);
		}

		Segmentation segmentation = MiningModelUtil.createSegmentation(MultipleModelMethodType.MAJORITY_VOTE, treeModels);

		Output output = ModelUtil.createProbabilityOutput(schema);

		MiningSchema miningSchema = ModelUtil.createMiningSchema(schema);

		MiningModel miningModel = new MiningModel(MiningFunctionType.CLASSIFICATION, miningSchema)
			.setSegmentation(segmentation)
			.setOutput(output);

		return miningModel;
	}

	private <P extends Number> TreeModel encodeTreeModel(MiningFunctionType miningFunction, List<? extends Number> leftDaughter, List<? extends Number> rightDaughter, ScoreEncoder<P> scoreEncoder, List<P> nodepred, List<? extends Number> bestvar, List<Double> xbestsplit, Schema schema){
		Node root = new Node()
			.setId("1")
			.setPredicate(new True());

		encodeNode(root, 0, leftDaughter, rightDaughter, bestvar, xbestsplit, scoreEncoder, nodepred, schema);

		MiningSchema miningSchema = ModelUtil.createMiningSchema(schema, root);

		TreeModel treeModel = new TreeModel(miningFunction, miningSchema, root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	private <P extends Number> void encodeNode(Node node, int i, List<? extends Number> leftDaughter, List<? extends Number> rightDaughter, List<? extends Number> bestvar, List<Double> xbestsplit, ScoreEncoder<P> scoreEncoder, List<P> nodepred, Schema schema){
		Predicate leftPredicate = null;
		Predicate rightPredicate = null;

		int var = ValueUtil.asInt(bestvar.get(i));
		if(var != 0){
			Feature feature = schema.getFeature(var - 1);

			Double split = xbestsplit.get(i);

			if(feature instanceof ListFeature){
				ListFeature listFeature = (ListFeature)feature;

				List<String> values = listFeature.getValues();

				leftPredicate = PredicateUtil.createSimpleSetPredicate(listFeature, selectValues(values, split, true));
				rightPredicate = PredicateUtil.createSimpleSetPredicate(listFeature, selectValues(values, split, false));
			} else

			if(feature instanceof ContinuousFeature){
				String value = ValueUtil.formatValue(split);

				leftPredicate = PredicateUtil.createSimplePredicate(feature, SimplePredicate.Operator.LESS_OR_EQUAL, value);
				rightPredicate = PredicateUtil.createSimplePredicate(feature, SimplePredicate.Operator.GREATER_THAN, value);
			} else

			{
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

			encodeNode(leftChild, left - 1, leftDaughter, rightDaughter, bestvar, xbestsplit, scoreEncoder, nodepred, schema);

			node.addNodes(leftChild);
		}

		int right = ValueUtil.asInt(rightDaughter.get(i));
		if(right != 0){
			Node rightChild = new Node()
				.setId(String.valueOf(right))
				.setPredicate(rightPredicate);

			encodeNode(rightChild, right - 1, leftDaughter, rightDaughter, bestvar, xbestsplit, scoreEncoder, nodepred, schema);

			node.addNodes(rightChild);
		}
	}

	static
	<E> List<E> selectValues(List<E> values, Double split, boolean left){
		UnsignedLong bits = toUnsignedLong(split.doubleValue());

		List<E> result = new ArrayList<>();

		for(int i = 0; i < values.size(); i++){
			E value = values.get(i);

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
	private List<String> getFactorLevels(RExp rexp){

		if(rexp instanceof RIntegerVector){
			RIntegerVector factor = (RIntegerVector)rexp;

			RStringVector levels = factor.getFactorLevels();

			return levels.getValues();
		}

		return null;
	}

	static
	private interface ScoreEncoder<K extends Number> {

		String encode(K key);
	}

	private static final UnsignedLong TWO = UnsignedLong.valueOf(2L);
}
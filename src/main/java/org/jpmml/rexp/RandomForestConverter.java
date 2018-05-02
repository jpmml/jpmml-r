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
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.Visitor;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.BooleanFeature;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.mining.MiningModelUtil;
import org.jpmml.rexp.visitors.RandomForestCompactor;

public class RandomForestConverter extends TreeModelConverter<RGenericVector> {

	public RandomForestConverter(RGenericVector randomForest){
		super(randomForest);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector randomForest = getObject();

		RExp terms = randomForest.getValue("terms", true);

		if(terms != null){
			encodeFormula(encoder);
		} else

		{
			encodeNonFormula(encoder);
		}
	}

	@Override
	public MiningModel encodeModel(Schema schema){
		RGenericVector randomForest = getObject();

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

	private void encodeFormula(RExpEncoder encoder){
		RGenericVector randomForest = getObject();

		RGenericVector forest = (RGenericVector)randomForest.getValue("forest");
		RNumberVector<?> y = (RNumberVector<?>)randomForest.getValue("y", true);
		RExp terms = randomForest.getValue("terms");

		RNumberVector<?> ncat = (RNumberVector<?>)forest.getValue("ncat");
		RGenericVector xlevels = (RGenericVector)forest.getValue("xlevels");

		FormulaContext context = new XLevelsFormulaContext(xlevels){

			@Override
			public List<String> getCategories(String variable){

				if(ncat != null && ncat.hasValue(variable)){

					if((ncat.getValue(variable)).doubleValue() > 1d){
						return super.getCategories(variable);
					}
				}

				return null;
			}
		};

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		if(y instanceof RIntegerVector){
			SchemaUtil.setLabel(formula, terms, y, encoder);
		} else

		{
			SchemaUtil.setLabel(formula, terms, null, encoder);
		}

		SchemaUtil.addFeatures(formula, xlevels.names(), false, encoder);
	}

	private void encodeNonFormula(RExpEncoder encoder){
		RGenericVector randomForest = getObject();

		RGenericVector forest = (RGenericVector)randomForest.getValue("forest");
		RNumberVector<?> y = (RNumberVector<?>)randomForest.getValue("y", true);
		RStringVector xNames = (RStringVector)randomForest.getValue("xNames", true);

		RNumberVector<?> ncat = (RNumberVector<?>)forest.getValue("ncat");
		RGenericVector xlevels = (RGenericVector)forest.getValue("xlevels");

		if(xNames == null){
			xNames = xlevels.names();
		}

		{
			FieldName name = FieldName.create("_target");

			DataField dataField;

			if(y instanceof RIntegerVector){
				dataField = encoder.createDataField(name, OpType.CATEGORICAL, null, RExpUtil.getFactorLevels(y));
			} else

			{
				dataField = encoder.createDataField(name, OpType.CONTINUOUS, DataType.DOUBLE);
			}

			encoder.setLabel(dataField);
		}

		for(int i = 0; i < ncat.size(); i++){
			FieldName name = FieldName.create(xNames.getValue(i));

			DataField dataField;

			boolean categorical = ((ncat.getValue(i)).doubleValue() > 1d);
			if(categorical){
				RStringVector levels = (RStringVector)xlevels.getValue(i);

				dataField = encoder.createDataField(name, OpType.CATEGORICAL, null, levels.getValues());
			} else

			{
				dataField = encoder.createDataField(name, OpType.CONTINUOUS, DataType.DOUBLE);
			}

			encoder.addFeature(dataField);
		}
	}

	private MiningModel encodeRegression(RGenericVector forest, Schema schema){
		RNumberVector<?> leftDaughter = (RNumberVector<?>)forest.getValue("leftDaughter");
		RNumberVector<?> rightDaughter = (RNumberVector<?>)forest.getValue("rightDaughter");
		RDoubleVector nodepred = (RDoubleVector)forest.getValue("nodepred");
		RNumberVector<?> bestvar = (RNumberVector<?>)forest.getValue("bestvar");
		RDoubleVector xbestsplit = (RDoubleVector)forest.getValue("xbestsplit");
		RIntegerVector nrnodes = (RIntegerVector)forest.getValue("nrnodes");
		RDoubleVector ntree = (RDoubleVector)forest.getValue("ntree");

		ScoreEncoder<Double> scoreEncoder = new ScoreEncoder<Double>(){

			@Override
			public String encode(Double value){
				return ValueUtil.formatValue(value);
			}
		};

		int rows = nrnodes.asScalar();
		int columns = ValueUtil.asInt(ntree.asScalar());

		Schema segmentSchema = schema.toAnonymousSchema();

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < columns; i++){
			TreeModel treeModel = encodeTreeModel(
					MiningFunction.REGRESSION,
					scoreEncoder,
					FortranMatrixUtil.getColumn(leftDaughter.getValues(), rows, columns, i),
					FortranMatrixUtil.getColumn(rightDaughter.getValues(), rows, columns, i),
					FortranMatrixUtil.getColumn(nodepred.getValues(), rows, columns, i),
					FortranMatrixUtil.getColumn(bestvar.getValues(), rows, columns, i),
					FortranMatrixUtil.getColumn(xbestsplit.getValues(), rows, columns, i),
					segmentSchema
				);

			treeModels.add(treeModel);
		}

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema.getLabel()))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.AVERAGE, treeModels));

		return miningModel;
	}

	private MiningModel encodeClassification(RGenericVector forest, Schema schema){
		RNumberVector<?> bestvar = (RNumberVector<?>)forest.getValue("bestvar");
		RNumberVector<?> treemap = (RNumberVector<?>)forest.getValue("treemap");
		RIntegerVector nodepred = (RIntegerVector)forest.getValue("nodepred");
		RDoubleVector xbestsplit = (RDoubleVector)forest.getValue("xbestsplit");
		RIntegerVector nrnodes = (RIntegerVector)forest.getValue("nrnodes");
		RDoubleVector ntree = (RDoubleVector)forest.getValue("ntree");

		int rows = nrnodes.asScalar();
		int columns = ValueUtil.asInt(ntree.asScalar());

		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		ScoreEncoder<Integer> scoreEncoder = new ScoreEncoder<Integer>(){

			@Override
			public String encode(Integer value){
				return categoricalLabel.getValue(value - 1);
			}
		};

		Schema segmentSchema = schema.toAnonymousSchema();

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < columns; i++){
			List<? extends Number> daughters = FortranMatrixUtil.getColumn(treemap.getValues(), 2 * rows, columns, i);

			TreeModel treeModel = encodeTreeModel(
					MiningFunction.CLASSIFICATION,
					scoreEncoder,
					FortranMatrixUtil.getColumn(daughters, rows, 2, 0),
					FortranMatrixUtil.getColumn(daughters, rows, 2, 1),
					FortranMatrixUtil.getColumn(nodepred.getValues(), rows, columns, i),
					FortranMatrixUtil.getColumn(bestvar.getValues(), rows, columns, i),
					FortranMatrixUtil.getColumn(xbestsplit.getValues(), rows, columns, i),
					segmentSchema
				);

			treeModels.add(treeModel);
		}

		MiningModel miningModel = new MiningModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.MAJORITY_VOTE, treeModels))
			.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));

		return miningModel;
	}

	private <P extends Number> TreeModel encodeTreeModel(MiningFunction miningFunction, ScoreEncoder<P> scoreEncoder, List<? extends Number> leftDaughter, List<? extends Number> rightDaughter, List<P> nodepred, List<? extends Number> bestvar, List<Double> xbestsplit, Schema schema){
		RGenericVector randomForest = getObject();

		RBooleanVector compact = (RBooleanVector)randomForest.getValue("compact", true);

		Node root = new Node()
			.setId("1")
			.setPredicate(new True());

		encodeNode(root, 0, scoreEncoder, leftDaughter, rightDaughter, bestvar, xbestsplit, nodepred, new CategoryManager(), schema);

		TreeModel treeModel = new TreeModel(miningFunction, ModelUtil.createMiningSchema(schema.getLabel()), root)
			.setMissingValueStrategy(TreeModel.MissingValueStrategy.NULL_PREDICTION)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		if(compact != null && compact.asScalar()){
			Visitor visitor = new RandomForestCompactor();

			visitor.applyTo(treeModel);
		}

		return treeModel;
	}

	private <P extends Number> void encodeNode(Node node, int i, ScoreEncoder<P> scoreEncoder, List<? extends Number> leftDaughter, List<? extends Number> rightDaughter, List<? extends Number> bestvar, List<Double> xbestsplit, List<P> nodepred, CategoryManager categoryManager, Schema schema){
		CategoryManager leftCategoryManager = categoryManager;
		CategoryManager rightCategoryManager = categoryManager;

		Predicate leftPredicate;
		Predicate rightPredicate;

		int var = ValueUtil.asInt(bestvar.get(i));
		if(var != 0){
			Feature feature = schema.getFeature(var - 1);

			Double split = xbestsplit.get(i);

			if(feature instanceof BooleanFeature){
				BooleanFeature booleanFeature = (BooleanFeature)feature;

				if(split != 0.5d){
					throw new IllegalArgumentException();
				}

				leftPredicate = createSimplePredicate(booleanFeature, SimplePredicate.Operator.EQUAL, booleanFeature.getValue(0));
				rightPredicate = createSimplePredicate(booleanFeature, SimplePredicate.Operator.EQUAL, booleanFeature.getValue(1));
			} else

			if(feature instanceof CategoricalFeature){
				CategoricalFeature categoricalFeature = (CategoricalFeature)feature;

				FieldName name = categoricalFeature.getName();
				List<String> values = categoricalFeature.getValues();

				java.util.function.Predicate<String> valueFilter = categoryManager.getValueFilter(name);

				List<String> leftValues = selectValues(values, valueFilter, split, true);
				List<String> rightValues = selectValues(values, valueFilter, split, false);

				leftCategoryManager = categoryManager.fork(name, leftValues);
				rightCategoryManager = categoryManager.fork(name, rightValues);

				leftPredicate = createSimpleSetPredicate(categoricalFeature, leftValues);
				rightPredicate = createSimpleSetPredicate(categoricalFeature, rightValues);
			} else

			{
				ContinuousFeature continuousFeature = feature.toContinuousFeature();

				String value = ValueUtil.formatValue(split);

				leftPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.LESS_OR_EQUAL, value);
				rightPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.GREATER_THAN, value);
			}
		} else

		{
			P prediction = nodepred.get(i);

			node.setScore(scoreEncoder.encode(prediction));

			return;
		}

		int left = ValueUtil.asInt(leftDaughter.get(i));
		if(left != 0){
			Node leftChild = new Node()
				.setId(String.valueOf(left))
				.setPredicate(leftPredicate);

			encodeNode(leftChild, left - 1, scoreEncoder, leftDaughter, rightDaughter, bestvar, xbestsplit, nodepred, leftCategoryManager, schema);

			node.addNodes(leftChild);
		}

		int right = ValueUtil.asInt(rightDaughter.get(i));
		if(right != 0){
			Node rightChild = new Node()
				.setId(String.valueOf(right))
				.setPredicate(rightPredicate);

			encodeNode(rightChild, right - 1, scoreEncoder, leftDaughter, rightDaughter, bestvar, xbestsplit, nodepred, rightCategoryManager, schema);

			node.addNodes(rightChild);
		}
	}

	static
	<E> List<E> selectValues(List<E> values, java.util.function.Predicate<E> valueFilter, Double split, boolean left){
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

			if(append && valueFilter.test(value)){
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
	private interface ScoreEncoder<V extends Number> {

		String encode(V value);
	}

	private static final UnsignedLong TWO = UnsignedLong.valueOf(2L);
}

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
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.Visitor;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.tree.BranchNode;
import org.dmg.pmml.tree.LeafNode;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.BooleanFeature;
import org.jpmml.converter.CMatrixUtil;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.CategoryManager;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.mining.MiningModelUtil;
import org.jpmml.rexp.visitors.RandomForestCompactor;

public class RandomForestConverter extends TreeModelConverter<RGenericVector> implements HasFeatureImportances {

	private boolean compact = true;


	public RandomForestConverter(RGenericVector randomForest){
		super(randomForest);

		this.compact = getOption("compact", Boolean.TRUE);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector randomForest = getObject();

		if(randomForest.hasElement("terms")){
			encodeFormula(encoder);
		} else

		{
			encodeNonFormula(encoder);
		}
	}

	@Override
	public MiningModel encodeModel(Schema schema){
		RGenericVector randomForest = getObject();

		RStringVector type = randomForest.getStringElement("type");
		RGenericVector forest = randomForest.getGenericElement("forest");

		switch(type.asScalar()){
			case "regression":
				return encodeRegression(forest, schema);
			case "classification":
				return encodeClassification(forest, schema);
			default:
				throw new IllegalArgumentException();
		}
	}

	@Override
	public FeatureImportanceMap getFeatureImportances(Schema schema){
		RGenericVector randomForest = getObject();

		RDoubleVector importance = randomForest.getDoubleElement("importance", false);

		if(importance == null){
			return null;
		}

		RStringVector importanceRows = importance.dimnames(0);
		RStringVector importanceColumns = importance.dimnames(1);

		RIntegerVector importanceDim = importance.dim();

		int rows = importanceDim.getValue(0);
		int columns = importanceDim.getValue(1);

		List<? extends Feature> features = schema.getFeatures();

		FeatureImportanceMap result = new FeatureImportanceMap(importanceColumns.getDequotedValue(columns - 1));

		List<Double> defaultImportances = CMatrixUtil.getColumn(importance.getValues(), rows, columns, columns - 1);

		for(int i = 0; i < features.size(); i++){
			result.put(features.get(i), defaultImportances.get(i));
		}

		return result;
	}

	private void encodeFormula(RExpEncoder encoder){
		RGenericVector randomForest = getObject();

		RGenericVector forest = randomForest.getGenericElement("forest");
		RNumberVector<?> y = randomForest.getNumericElement("y", false);
		RExp terms = randomForest.getElement("terms");

		RNumberVector<?> ncat = forest.getNumericElement("ncat");
		RGenericVector xlevels = forest.getGenericElement("xlevels");

		FormulaContext context = new XLevelsFormulaContext(xlevels){

			@Override
			public List<String> getCategories(String variable){

				if(ncat != null && ncat.hasElement(variable)){

					if((ncat.getElement(variable)).doubleValue() > 1d){
						return super.getCategories(variable);
					}
				}

				return null;
			}
		};

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		if(y instanceof RIntegerVector){
			FormulaUtil.setLabel(formula, terms, y, encoder);
		} else

		{
			FormulaUtil.setLabel(formula, terms, null, encoder);
		}

		FormulaUtil.addFeatures(formula, xlevels.names(), false, encoder);
	}

	private void encodeNonFormula(RExpEncoder encoder){
		RGenericVector randomForest = getObject();

		RGenericVector forest = randomForest.getGenericElement("forest");
		RNumberVector<?> y = randomForest.getNumericElement("y", false);
		RStringVector xNames = randomForest.getStringElement("xNames", false);

		RNumberVector<?> ncat = forest.getNumericElement("ncat");
		RGenericVector xlevels = forest.getGenericElement("xlevels");

		if(xNames == null){
			xNames = xlevels.names();
		}

		{
			String name = "_target";

			DataField dataField;

			if(y instanceof RIntegerVector){
				y = randomForest.getFactorElement("y");

				dataField = encoder.createDataField(name, OpType.CATEGORICAL, null, ((RFactorVector)y).getLevelValues());
			} else

			{
				dataField = encoder.createDataField(name, OpType.CONTINUOUS, DataType.DOUBLE);
			}

			encoder.setLabel(dataField);
		}

		for(int i = 0; i < ncat.size(); i++){
			String name = xNames.getValue(i);

			DataField dataField;

			boolean categorical = ((ncat.getValue(i)).doubleValue() > 1d);
			if(categorical){
				RStringVector levels = xlevels.getStringValue(i);

				dataField = encoder.createDataField(name, OpType.CATEGORICAL, null, levels.getValues());
			} else

			{
				dataField = encoder.createDataField(name, OpType.CONTINUOUS, DataType.DOUBLE);
			}

			encoder.addFeature(dataField);
		}
	}

	private MiningModel encodeRegression(RGenericVector forest, Schema schema){
		RNumberVector<?> leftDaughter = forest.getNumericElement("leftDaughter");
		RNumberVector<?> rightDaughter = forest.getNumericElement("rightDaughter");
		RDoubleVector nodepred = forest.getDoubleElement("nodepred");
		RNumberVector<?> bestvar = forest.getNumericElement("bestvar");
		RDoubleVector xbestsplit = forest.getDoubleElement("xbestsplit");
		RIntegerVector nrnodes = forest.getIntegerElement("nrnodes");
		RNumberVector<?> ntree = forest.getNumericElement("ntree");

		ScoreEncoder<Double> scoreEncoder = new ScoreEncoder<Double>(){

			@Override
			public Double encode(Double value){
				return value;
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
		RNumberVector<?> bestvar = forest.getNumericElement("bestvar");
		RNumberVector<?> treemap = forest.getNumericElement("treemap");
		RIntegerVector nodepred = forest.getIntegerElement("nodepred");
		RDoubleVector xbestsplit = forest.getDoubleElement("xbestsplit");
		RIntegerVector nrnodes = forest.getIntegerElement("nrnodes");
		RDoubleVector ntree = forest.getDoubleElement("ntree");

		int rows = nrnodes.asScalar();
		int columns = ValueUtil.asInt(ntree.asScalar());

		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		ScoreEncoder<Integer> scoreEncoder = new ScoreEncoder<Integer>(){

			@Override
			public Object encode(Integer value){
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

		Node root = encodeNode(True.INSTANCE, 0, scoreEncoder, leftDaughter, rightDaughter, bestvar, xbestsplit, nodepred, new CategoryManager(), schema);

		TreeModel treeModel = new TreeModel(miningFunction, ModelUtil.createMiningSchema(schema.getLabel()), root)
			.setMissingValueStrategy(TreeModel.MissingValueStrategy.NULL_PREDICTION)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		if(this.compact){
			Visitor visitor = new RandomForestCompactor();

			visitor.applyTo(treeModel);
		}

		return treeModel;
	}

	private <P extends Number> Node encodeNode(Predicate predicate, int i, ScoreEncoder<P> scoreEncoder, List<? extends Number> leftDaughter, List<? extends Number> rightDaughter, List<? extends Number> bestvar, List<Double> xbestsplit, List<P> nodepred, CategoryManager categoryManager, Schema schema){
		Integer id = Integer.valueOf(i + 1);

		int var = ValueUtil.asInt(bestvar.get(i));
		if(var == 0){
			P prediction = nodepred.get(i);

			Node result = new LeafNode(scoreEncoder.encode(prediction), predicate)
				.setId(id);

			return result;
		}

		CategoryManager leftCategoryManager = categoryManager;
		CategoryManager rightCategoryManager = categoryManager;

		Predicate leftPredicate;
		Predicate rightPredicate;

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

			String name = categoricalFeature.getName();
			List<?> values = categoricalFeature.getValues();

			java.util.function.Predicate<Object> valueFilter = categoryManager.getValueFilter(name);

			List<Object> leftValues = selectValues(values, valueFilter, split, true);
			List<Object> rightValues = selectValues(values, valueFilter, split, false);

			leftCategoryManager = categoryManager.fork(name, leftValues);
			rightCategoryManager = categoryManager.fork(name, rightValues);

			leftPredicate = createPredicate(categoricalFeature, leftValues);
			rightPredicate = createPredicate(categoricalFeature, rightValues);
		} else

		{
			ContinuousFeature continuousFeature = feature.toContinuousFeature();

			leftPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.LESS_OR_EQUAL, split);
			rightPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.GREATER_THAN, split);
		}

		Node result = new BranchNode(null, predicate)
			.setId(id);

		List<Node> nodes = result.getNodes();

		int left = ValueUtil.asInt(leftDaughter.get(i));
		if(left != 0){
			Node leftChild = encodeNode(leftPredicate, left - 1, scoreEncoder, leftDaughter, rightDaughter, bestvar, xbestsplit, nodepred, leftCategoryManager, schema);

			nodes.add(leftChild);
		}

		int right = ValueUtil.asInt(rightDaughter.get(i));
		if(right != 0){
			Node rightChild = encodeNode(rightPredicate, right - 1, scoreEncoder, leftDaughter, rightDaughter, bestvar, xbestsplit, nodepred, rightCategoryManager, schema);

			nodes.add(rightChild);
		}

		return result;
	}

	static
	List<Object> selectValues(List<?> values, java.util.function.Predicate<Object> valueFilter, Double split, boolean left){
		UnsignedLong bits = toUnsignedLong(split.doubleValue());

		List<Object> result = new ArrayList<>();

		for(int i = 0; i < values.size(); i++){
			Object value = values.get(i);

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

		Object encode(V value);
	}

	private static final UnsignedLong TWO = UnsignedLong.valueOf(2L);
}

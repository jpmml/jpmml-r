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
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.Value;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.BooleanFeature;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ListFeature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.WildcardFeature;
import org.jpmml.converter.mining.MiningModelUtil;

public class RandomForestConverter extends TreeModelConverter<RGenericVector> {

	public RandomForestConverter(RGenericVector randomForest){
		super(randomForest);
	}

	@Override
	public void encodeFeatures(FeatureMapper featureMapper){
		RGenericVector randomForest = getObject();

		RGenericVector forest = (RGenericVector)randomForest.getValue("forest");
		RNumberVector<?> y = (RNumberVector<?>)randomForest.getValue("y", true);
		RExp terms = randomForest.getValue("terms", true);

		RNumberVector<?> ncat = (RNumberVector<?>)forest.getValue("ncat");
		RGenericVector xlevels = (RGenericVector)forest.getValue("xlevels");

		// The RF model was trained using the formula interface
		if(terms != null){
			encodeFormula(terms, y, xlevels, ncat, featureMapper);
		} else

		// The RF model was trained using the matrix (ie. non-formula) interface
		{
			RStringVector xNames = (RStringVector)randomForest.getValue("xNames", true);

			if(xNames == null){
				xNames = xlevels.names();
			}

			encodeNonFormula(xNames, y, xlevels, ncat, featureMapper);
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

	private void encodeFormula(RExp terms, RNumberVector<?> y, final RGenericVector xlevels, final RNumberVector<?> ncat, FeatureMapper featureMapper){
		RIntegerVector response = (RIntegerVector)terms.getAttributeValue("response");

		FormulaContext context = new FormulaContext(){

			@Override
			public List<String> getCategories(String variable){

				if(ncat != null && ncat.hasValue(variable)){

					if((ncat.getValue(variable)).doubleValue() > 1d){
						RStringVector levels = (RStringVector)xlevels.getValue(variable);

						return levels.getValues();
					}
				}

				return null;
			}

			@Override
			public RGenericVector getData(){
				return null;
			}
		};

		Formula formula = FormulaUtil.encodeFeatures(context, terms, featureMapper);

		// Dependent variable
		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			DataField dataField = (DataField)formula.getField(responseIndex - 1);

			if(y instanceof RIntegerVector){
				RIntegerVector factor = (RIntegerVector)y;

				if(!factor.isFactor()){
					throw new IllegalArgumentException();
				} // End if

				if(dataField.hasValues()){
					throw new IllegalArgumentException();
				}

				List<Value> values = dataField.getValues();

				values.addAll(PMMLUtil.createValues(factor.getLevelValues()));
			}

			Feature feature = new WildcardFeature(dataField);

			featureMapper.append(feature);
		} else

		{
			throw new IllegalArgumentException();
		}

		RStringVector xlevelNames = xlevels.names();

		// Independent variables
		for(int i = 0; i < xlevelNames.size(); i++){
			String xlevelName = xlevelNames.getValue(i);

			Feature feature = formula.resolveFeature(FieldName.create(xlevelName));

			featureMapper.append(feature);
		}
	}

	private void encodeNonFormula(RStringVector xNames, RNumberVector<?> y, RGenericVector xlevels, RNumberVector<?> ncat, FeatureMapper featureMapper){

		// Dependent variable
		{
			FieldName name = FieldName.create("_target");

			if(y instanceof RIntegerVector){
				RIntegerVector factor = (RIntegerVector)y;

				if(!factor.isFactor()){
					throw new IllegalArgumentException();
				}

				featureMapper.append(name, factor.getLevelValues());
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
					RExpUtil.getColumn(leftDaughter.getValues(), rows, columns, i),
					RExpUtil.getColumn(rightDaughter.getValues(), rows, columns, i),
					RExpUtil.getColumn(nodepred.getValues(), rows, columns, i),
					RExpUtil.getColumn(bestvar.getValues(), rows, columns, i),
					RExpUtil.getColumn(xbestsplit.getValues(), rows, columns, i),
					segmentSchema
				);

			treeModels.add(treeModel);
		}

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.AVERAGE, treeModels));

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
			public String encode(Integer value){
				return this.targetCategories.get(value - 1);
			}
		};

		int rows = nrnodes.asScalar();
		int columns = ValueUtil.asInt(ntree.asScalar());

		Schema segmentSchema = schema.toAnonymousSchema();

		List<TreeModel> treeModels = new ArrayList<>();

		for(int i = 0; i < columns; i++){
			List<? extends Number> daughters = RExpUtil.getColumn(treemap.getValues(), 2 * rows, columns, i);

			TreeModel treeModel = encodeTreeModel(
					MiningFunction.CLASSIFICATION,
					scoreEncoder,
					RExpUtil.getColumn(daughters, rows, columns, 0),
					RExpUtil.getColumn(daughters, rows, columns, 1),
					RExpUtil.getColumn(nodepred.getValues(), rows, columns, i),
					RExpUtil.getColumn(bestvar.getValues(), rows, columns, i),
					RExpUtil.getColumn(xbestsplit.getValues(), rows, columns, i),
					segmentSchema
				);

			treeModels.add(treeModel);
		}

		MiningModel miningModel = new MiningModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(schema))
			.setSegmentation(MiningModelUtil.createSegmentation(Segmentation.MultipleModelMethod.MAJORITY_VOTE, treeModels))
			.setOutput(ModelUtil.createProbabilityOutput(schema));

		return miningModel;
	}

	private <P extends Number> TreeModel encodeTreeModel(MiningFunction miningFunction, ScoreEncoder<P> scoreEncoder, List<? extends Number> leftDaughter, List<? extends Number> rightDaughter, List<P> nodepred, List<? extends Number> bestvar, List<Double> xbestsplit, Schema schema){
		Node root = new Node()
			.setId("1")
			.setPredicate(new True());

		encodeNode(root, 0, scoreEncoder, leftDaughter, rightDaughter, bestvar, xbestsplit, nodepred, schema);

		TreeModel treeModel = new TreeModel(miningFunction, ModelUtil.createMiningSchema(schema), root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT);

		return treeModel;
	}

	private <P extends Number> void encodeNode(Node node, int i, ScoreEncoder<P> scoreEncoder, List<? extends Number> leftDaughter, List<? extends Number> rightDaughter, List<? extends Number> bestvar, List<Double> xbestsplit, List<P> nodepred, Schema schema){
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

			if(feature instanceof ListFeature){
				ListFeature listFeature = (ListFeature)feature;

				List<String> values = listFeature.getValues();

				leftPredicate = createSimpleSetPredicate(listFeature, selectValues(values, split, true));
				rightPredicate = createSimpleSetPredicate(listFeature, selectValues(values, split, false));
			} else

			if(feature instanceof ContinuousFeature){
				String value = ValueUtil.formatValue(split);

				leftPredicate = createSimplePredicate(feature, SimplePredicate.Operator.LESS_OR_EQUAL, value);
				rightPredicate = createSimplePredicate(feature, SimplePredicate.Operator.GREATER_THAN, value);
			} else

			{
				throw new IllegalArgumentException();
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

			encodeNode(leftChild, left - 1, scoreEncoder, leftDaughter, rightDaughter, bestvar, xbestsplit, nodepred, schema);

			node.addNodes(leftChild);
		}

		int right = ValueUtil.asInt(rightDaughter.get(i));
		if(right != 0){
			Node rightChild = new Node()
				.setId(String.valueOf(right))
				.setPredicate(rightPredicate);

			encodeNode(rightChild, right - 1, scoreEncoder, leftDaughter, rightDaughter, bestvar, xbestsplit, nodepred, schema);

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
	private interface ScoreEncoder<V extends Number> {

		String encode(V value);
	}

	private static final UnsignedLong TWO = UnsignedLong.valueOf(2L);
}

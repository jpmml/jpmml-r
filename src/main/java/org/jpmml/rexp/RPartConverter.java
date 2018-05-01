/*
 * Copyright (c) 2018 Villu Ruusmann
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

import org.dmg.pmml.CompoundPredicate;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;

public class RPartConverter extends TreeModelConverter<RGenericVector> {

	private int useSurrogate = 0;

	private Formula formula = null;


	public RPartConverter(RGenericVector rpart){
		super(rpart);

		RGenericVector control = (RGenericVector)rpart.getValue("control");

		RNumberVector<?> useSurrogate = (RNumberVector<?>)control.getValue("usesurrogate");

		this.useSurrogate = ValueUtil.asInt(useSurrogate.asScalar());

		switch(this.useSurrogate){
			case 0:
			case 1:
			case 2:
				break;
			default:
				throw new IllegalArgumentException();
		}
	}

	public boolean hasScoreDistribution(){
		return true;
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector rpart = getObject();

		RGenericVector frame = (RGenericVector)rpart.getValue("frame");
		RExp terms = rpart.getValue("terms");

		RGenericVector xlevels = (RGenericVector)rpart.getAttributeValue("xlevels", true);
		RStringVector ylevels = (RStringVector)rpart.getAttributeValue("ylevels", true);

		RIntegerVector var = (RIntegerVector)frame.getValue("var");

		FormulaContext context = new XLevelsFormulaContext(xlevels);

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		SchemaUtil.setLabel(formula, terms, ylevels, encoder);

		List<String> names = SchemaUtil.removeSpecialSymbol(RExpUtil.getFactorLevels(var), "<leaf>", 0);

		SchemaUtil.addFeatures(formula, names, false, encoder);

		this.formula = formula;
	}

	@Override
	public TreeModel encodeModel(Schema schema){
		RGenericVector rpart = getObject();

		RGenericVector frame = (RGenericVector)rpart.getValue("frame");
		RStringVector method = (RStringVector)rpart.getValue("method");
		RNumberVector<?> splits = (RNumberVector<?>)rpart.getValue("splits");
		RIntegerVector csplit = (RIntegerVector)rpart.getValue("csplit", true);

		RIntegerVector var = (RIntegerVector)frame.getValue("var");
		RIntegerVector n = (RIntegerVector)frame.getValue("n");
		RIntegerVector ncompete = (RIntegerVector)frame.getValue("ncompete");
		RIntegerVector nsurrogate = (RIntegerVector)frame.getValue("nsurrogate");

		RIntegerVector rowNames = (RIntegerVector)frame.getAttributeValue("row.names");

		if((rowNames.getValues()).indexOf(Integer.MIN_VALUE) > -1){
			throw new IllegalArgumentException();
		}

		int[][] splitInfo = new int[1 + rowNames.size()][3];

		for(int offset = 0; offset < rowNames.size(); offset++){
			splitInfo[offset][1] = ncompete.getValue(offset);
			splitInfo[offset][2] = nsurrogate.getValue(offset);

			splitInfo[offset + 1][0] = splitInfo[offset][0] + splitInfo[offset][1] + splitInfo[offset][2] + (var.getValue(offset) != 1 ? 1 : 0);
		}

		switch(method.asScalar()){
			case "anova":
				return encodeRegression(frame, rowNames, var, n, splitInfo, splits, csplit, schema);
			case "class":
				return encodeClassification(frame, rowNames, var, n, splitInfo, splits, csplit, schema);
			default:
				throw new IllegalArgumentException();
		}
	}

	private TreeModel encodeRegression(RGenericVector frame, RIntegerVector rowNames, RIntegerVector var, RIntegerVector n, int[][] splitInfo, RNumberVector<?> splits, RIntegerVector csplit, Schema schema){
		RNumberVector<?> yval = (RNumberVector<?>)frame.getValue("yval");

		ScoreEncoder scoreEncoder = new ScoreEncoder(){

			@Override
			public void encode(Node node, int offset){
				Number score = yval.getValue(offset);
				Number recordCount = n.getValue(offset);

				node
					.setScore(ValueUtil.formatValue(score))
					.setRecordCount(recordCount.doubleValue());
			}
		};

		Node root = new Node()
			.setPredicate(new True());

		encodeNode(root, 1, rowNames, var, n, splitInfo, splits, csplit, scoreEncoder, schema);

		TreeModel treeModel = new TreeModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema.getLabel()), root);

		return configureTreeModel(treeModel);
	}

	private TreeModel encodeClassification(RGenericVector frame, RIntegerVector rowNames, RIntegerVector var, RIntegerVector n, int[][] splitInfo, RNumberVector<?> splits, RIntegerVector csplit, Schema schema){
		RDoubleVector yval2 = (RDoubleVector)frame.getValue("yval2");

		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		List<String> categories = categoricalLabel.getValues();

		final
		boolean hasScoreDistribution = hasScoreDistribution();

		ScoreEncoder scoreEncoder = new ScoreEncoder(){

			private List<Integer> classes = null;

			private List<List<? extends Number>> recordCounts = null;


			{
				int rows = rowNames.size();
				int columns = 1 + (2 * categories.size()) + 1;

				List<Integer> classes = ValueUtil.asIntegers(FortranMatrixUtil.getColumn(yval2.getValues(), rows, columns, 0));

				this.classes = new ArrayList<>(classes);

				if(hasScoreDistribution){
					this.recordCounts = new ArrayList<>();

					for(int i = 0; i < categories.size(); i++){
						List<? extends Number> recordCounts = FortranMatrixUtil.getColumn(yval2.getValues(), rows, columns, 1 + i);

						this.recordCounts.add(new ArrayList<>(recordCounts));
					}
				}
			}

			@Override
			public void encode(Node node, int offset){
				String score = categories.get(this.classes.get(offset) - 1);
				Integer recordCount = n.getValue(offset);

				node
					.setScore(score)
					.setRecordCount(recordCount.doubleValue());

				if(hasScoreDistribution){

					for(int i = 0; i < categories.size(); i++){
						List<? extends Number> recordCounts = this.recordCounts.get(i);

						ScoreDistribution scoreDistribution = new ScoreDistribution()
							.setValue(categories.get(i))
							.setRecordCount(recordCounts.get(offset).doubleValue());

						node.addScoreDistributions(scoreDistribution);
					}
				}
			}
		};

		Node root = new Node()
			.setPredicate(new True());

		encodeNode(root, 1, rowNames, var, n, splitInfo, splits, csplit, scoreEncoder, schema);

		TreeModel treeModel = new TreeModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(schema.getLabel()), root);

		if(hasScoreDistribution){
			treeModel.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));
		}

		return configureTreeModel(treeModel);
	}

	private TreeModel configureTreeModel(TreeModel treeModel){
		TreeModel.NoTrueChildStrategy noTrueChildStrategy = TreeModel.NoTrueChildStrategy.RETURN_LAST_PREDICTION;
		TreeModel.MissingValueStrategy missingValueStrategy;

		switch(this.useSurrogate){
			case 0:
				missingValueStrategy = TreeModel.MissingValueStrategy.NULL_PREDICTION; // XXX
				break;
			case 1:
				missingValueStrategy = TreeModel.MissingValueStrategy.LAST_PREDICTION;
				break;
			case 2:
				missingValueStrategy = null;
				break;
			default:
				throw new IllegalArgumentException();
		}

		treeModel
			.setNoTrueChildStrategy(noTrueChildStrategy)
			.setMissingValueStrategy(missingValueStrategy);

		return treeModel;
	}

	private void encodeNode(Node node, int rowName, RIntegerVector rowNames, RIntegerVector var, RIntegerVector n, int[][] splitInfo, RNumberVector<?> splits, RIntegerVector csplit, ScoreEncoder scoreEncoder, Schema schema){
		int offset = getIndex(rowNames, rowName);

		node.setId(String.valueOf(rowName));

		scoreEncoder.encode(node, offset);

		int splitVar = var.getValue(offset) - 1;
		if(splitVar == 0){
			return;
		}

		int leftRowName = rowName * 2;
		int rightRowName = (rowName * 2) + 1;

		Integer majorityDir = null;

		if(this.useSurrogate == 2){
			int leftOffset = getIndex(rowNames, leftRowName);
			int rightOffset = getIndex(rowNames, rightRowName);

			majorityDir = Double.compare(n.getValue(leftOffset), n.getValue(rightOffset));
		}

		Feature feature = schema.getFeature(splitVar - 1);

		int splitOffset = splitInfo[offset][0];

		int splitNumCompete = splitInfo[offset][1];
		int splitNumSurrogate = splitInfo[offset][2];

		List<Predicate> predicates = encodePredicates(feature, splitOffset, splits, csplit);

		Predicate leftPredicate = predicates.get(0);
		Predicate rightPredicate = predicates.get(1);

		if(this.useSurrogate > 0 && splitNumSurrogate > 0){
			CompoundPredicate leftCompoundPredicate = new CompoundPredicate(CompoundPredicate.BooleanOperator.SURROGATE)
				.addPredicates(leftPredicate);

			CompoundPredicate rightCompoundPredicate = new CompoundPredicate(CompoundPredicate.BooleanOperator.SURROGATE)
				.addPredicates(rightPredicate);

			RStringVector splitRowNames = splits.dimnames(0);

			for(int i = 0; i < splitNumSurrogate; i++){
				int surrogateSplitOffset = (splitOffset + 1) + splitNumCompete + i;

				feature = getFeature(FieldName.create(splitRowNames.getValue(surrogateSplitOffset)));

				predicates = encodePredicates(feature, surrogateSplitOffset, splits, csplit);

				leftCompoundPredicate.addPredicates(predicates.get(0));
				rightCompoundPredicate.addPredicates(predicates.get(1));
			}

			leftPredicate = leftCompoundPredicate;
			rightPredicate = rightCompoundPredicate;
		}

		Node leftChild = new Node()
			.setPredicate(leftPredicate);

		Node rightChild = new Node()
			.setPredicate(rightPredicate);

		encodeNode(leftChild, leftRowName, rowNames, var, n, splitInfo, splits, csplit, scoreEncoder, schema);
		encodeNode(rightChild, rightRowName, rowNames, var, n, splitInfo, splits, csplit, scoreEncoder, schema);

		if(this.useSurrogate == 2){

			if(majorityDir < 0){
				makeDefault(rightChild);
			} else

			if(majorityDir > 0){
				Node tmp = leftChild;

				makeDefault(leftChild);

				leftChild = rightChild;
				rightChild = tmp;
			}
		}

		node.addNodes(leftChild, rightChild);
	}

	private List<Predicate> encodePredicates(Feature feature, int splitOffset, RNumberVector<?> splits, RIntegerVector csplit){
		Predicate leftPredicate;
		Predicate rightPredicate;

		RIntegerVector splitsDim = splits.dim();

		int splitRows = splitsDim.getValue(0);
		int splitColumns = splitsDim.getValue(1);

		List<? extends Number> ncat = FortranMatrixUtil.getColumn(splits.getValues(), splitRows, splitColumns, 1);
		List<? extends Number> index = FortranMatrixUtil.getColumn(splits.getValues(), splitRows, splitColumns, 3);

		int splitType = ValueUtil.asInt(ncat.get(splitOffset));

		Number splitValue = index.get(splitOffset);

		if(Math.abs(splitType) == 1){
			SimplePredicate.Operator leftOperator;
			SimplePredicate.Operator rightOperator;

			if(splitType == -1){
				leftOperator = SimplePredicate.Operator.LESS_THAN;
				rightOperator = SimplePredicate.Operator.GREATER_OR_EQUAL;
			} else

			{
				leftOperator = SimplePredicate.Operator.GREATER_OR_EQUAL;
				rightOperator = SimplePredicate.Operator.LESS_THAN;
			}

			String value = ValueUtil.formatValue(splitValue);

			leftPredicate = createSimplePredicate(feature, leftOperator, value);
			rightPredicate = createSimplePredicate(feature, rightOperator, value);
		} else

		{
			CategoricalFeature categoricalFeature = (CategoricalFeature)feature;

			RIntegerVector csplitDim = csplit.dim();

			int csplitRows = csplitDim.getValue(0);
			int csplitColumns = csplitDim.getValue(1);

			List<Integer> csplitRow = FortranMatrixUtil.getRow(csplit.getValues(), csplitRows, csplitColumns, ValueUtil.asInt(splitValue) - 1);

			List<String> values = categoricalFeature.getValues();

			leftPredicate = createSimpleSetPredicate(categoricalFeature, selectValues(values, csplitRow, 1));
			rightPredicate = createSimpleSetPredicate(categoricalFeature, selectValues(values, csplitRow, 3));
		}

		return Arrays.asList(leftPredicate, rightPredicate);
	}

	private void makeDefault(Node node){
		Predicate predicate = node.getPredicate();

		CompoundPredicate compoundPredicate;

		if(predicate instanceof CompoundPredicate){
			compoundPredicate = (CompoundPredicate)predicate;
		} else

		{
			compoundPredicate = new CompoundPredicate(CompoundPredicate.BooleanOperator.SURROGATE)
				.addPredicates(predicate);

			node.setPredicate(compoundPredicate);
		}

		compoundPredicate.addPredicates(new True());
	}

	private Feature getFeature(FieldName name){
		return this.formula.resolveFeature(name);
	}

	static
	private int getIndex(RIntegerVector rowNames, int rowName){
		int index = rowNames.indexOf(rowName);
		if(index < 0){
			throw new IllegalArgumentException();
		}

		return index;
	}

	static
	private List<String> selectValues(List<String> values, List<Integer> valueFlags, int flag){
		List<String> result = new ArrayList<>(values.size());

		for(int i = 0; i < values.size(); i++){
			String value = values.get(i);
			Integer valueFlag = valueFlags.get(i);

			if(valueFlag == flag){
				result.add(value);
			}
		}

		return result;
	}

	static
	private interface ScoreEncoder {

		void encode(Node node, int offset);
	}
}
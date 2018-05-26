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
import java.util.List;

import org.dmg.pmml.DataType;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.Label;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;

public class PartyConverter extends TreeModelConverter<RGenericVector> {

	public PartyConverter(RGenericVector party){
		super(party);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector party = getObject();

		RGenericVector data = (RGenericVector)party.getValue("data");
		RGenericVector fitted = (RGenericVector)party.getValue("fitted");
		RExp terms = party.getValue("terms");

		RIntegerVector factors = (RIntegerVector)terms.getAttributeValue("factors");
		RIntegerVector response = (RIntegerVector)terms.getAttributeValue("response");

		RStringVector variableRows = factors.dimnames(0);
		RStringVector termColumns = factors.dimnames(1);

		String responseVariable;

		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			responseVariable = variableRows.getDequotedValue(responseIndex - 1);
		} else

		{
			responseVariable = null;
		}

		FormulaContext context = new FormulaContext(){

			@Override
			public List<String> getCategories(String variable){
				RVector<?> data = getData(variable);

				if(data != null && RExpUtil.isFactor(data)){
					RIntegerVector factor = (RIntegerVector)data;

					return factor.getLevelValues();
				}

				return null;
			}

			@Override
			public RVector<?> getData(String variable){

				if(data.hasValue(variable)){
					return (RVector<?>)data.getValue(variable);
				} // End if

				if((variable).equals(responseVariable)){
					return (RVector<?>)fitted.getValue("(response)");
				}

				return null;
			}
		};

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		RIntegerVector levels = null;

		if(responseIndex != 0){
			RVector<?> responseData = context.getData(responseVariable);

			if(responseData != null && RExpUtil.isFactor(responseData)){
				levels = (RIntegerVector)responseData;
			}
		} else

		{
			throw new IllegalArgumentException();
		}

		SchemaUtil.setLabel(formula, terms, levels, encoder);

		SchemaUtil.addFeatures(formula, termColumns, false, encoder);
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector party = getObject();

		RGenericVector partyNode = (RGenericVector)party.getValue("node");

		RGenericVector predicted = (RGenericVector)DecorationUtil.getValue(party, "predicted");

		RVector<?> response = (RVector<?>)predicted.getValue("(response)");
		RDoubleVector prob = (RDoubleVector)predicted.getValue("(prob)", true);

		Node root = new Node()
			.setPredicate(new True());

		encodeNode(root, partyNode, response, prob, schema);

		TreeModel treeModel;

		if(RExpUtil.isFactor(response)){
			CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

			treeModel = new TreeModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel), root)
				.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));
		} else

		{
			treeModel = new TreeModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema.getLabel()), root);
		}

		return treeModel;
	}

	private void encodeNode(Node node, RGenericVector partyNode, RVector<?> response, RDoubleVector prob, Schema schema){
		RIntegerVector id = (RIntegerVector)partyNode.getValue("id");
		RGenericVector split = (RGenericVector)partyNode.getValue("split");
		RGenericVector kids = (RGenericVector)partyNode.getValue("kids");
		RGenericVector surrogates = (RGenericVector)partyNode.getValue("surrogates");
		RGenericVector info = (RGenericVector)partyNode.getValue("info");

		if(surrogates != null){
			throw new IllegalArgumentException();
		}

		Label label = schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		node.setId(String.valueOf(id.asScalar()));

		if(RExpUtil.isFactor(response)){
			RIntegerVector factor = (RIntegerVector)response;

			int index = id.asScalar() - 1;

			node.setScore(factor.getFactorValue(index));

			CategoricalLabel categoricalLabel = (CategoricalLabel)label;

			List<Double> probabilities = FortranMatrixUtil.getRow(prob.getValues(), response.size(), categoricalLabel.size(), index);

			for(int i = 0; i < categoricalLabel.size(); i++){
				String value = categoricalLabel.getValue(i);
				Double probability = probabilities.get(i);

				ScoreDistribution scoreDistribution = new ScoreDistribution(value, probability);

				node.addScoreDistributions(scoreDistribution);
			}
		} else

		{
			node.setScore(ValueUtil.formatValue(response.getValue(id.asScalar() - 1)));
		} // End if

		if(kids == null){
			return;
		}

		RIntegerVector varid = (RIntegerVector)split.getValue("varid");
		RDoubleVector breaks = (RDoubleVector)split.getValue("breaks");
		RIntegerVector index = (RIntegerVector)split.getValue("index");
		RBooleanVector right = (RBooleanVector)split.getValue("right");

		Feature feature = features.get(varid.asScalar() - 1);

		if(breaks != null && index == null){
			ContinuousFeature continuousFeature = (ContinuousFeature)feature;

			if(kids.size() != 2){
				throw new IllegalArgumentException();
			} // End if

			if(breaks.size() != 1){
				throw new IllegalArgumentException();
			}

			Predicate leftPredicate;
			Predicate rightPredicate;

			String value = ValueUtil.formatValue(breaks.asScalar());

			if(right.asScalar()){
				leftPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.LESS_OR_EQUAL, value);
				rightPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.GREATER_THAN, value);
			} else

			{
				leftPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.LESS_THAN, value);
				rightPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.GREATER_OR_EQUAL, value);
			}

			Node leftChild = new Node()
				.setPredicate(leftPredicate);

			Node rightChild = new Node()
				.setPredicate(rightPredicate);

			encodeNode(leftChild, (RGenericVector)kids.getValue(0), response, prob, schema);
			encodeNode(rightChild, (RGenericVector)kids.getValue(1), response, prob, schema);

			node.addNodes(leftChild, rightChild);
		} else

		if(breaks == null && index != null){
			CategoricalFeature categoricalFeature = (CategoricalFeature)feature;

			if(kids.size() < 2){
				throw new IllegalArgumentException();
			}

			List<String> values = categoricalFeature.getValues();

			for(int i = 0; i < kids.size(); i++){
				Predicate predicate;

				if(right.asScalar()){
					predicate = createSimpleSetPredicate(categoricalFeature, selectValues(values, index, i + 1));
				} else

				{
					throw new IllegalArgumentException();
				}

				Node child = new Node()
					.setPredicate(predicate);

				encodeNode(child, (RGenericVector)kids.getValue(i), response, prob, schema);

				node.addNodes(child);
			}
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	static
	private List<String> selectValues(List<String> values, RIntegerVector index, int flag){
		List<String> result = new ArrayList<>();

		if(values.size() != index.size()){
			throw new IllegalArgumentException();
		}

		for(int i = 0; i < values.size(); i++){
			String value = values.get(i);

			if(index.getValue(i) == flag){
				result.add(value);
			}
		}

		return result;
	}
}
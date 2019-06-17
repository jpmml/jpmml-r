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
import org.dmg.pmml.tree.BranchNode;
import org.dmg.pmml.tree.ClassifierNode;
import org.dmg.pmml.tree.LeafNode;
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

public class PartyConverter extends TreeModelConverter<RGenericVector> {

	public PartyConverter(RGenericVector party){
		super(party);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector party = getObject();

		RGenericVector data = party.getGenericElement("data");
		RGenericVector fitted = party.getGenericElement("fitted");
		RExp terms = party.getElement("terms");

		RIntegerVector factors = terms.getIntegerAttribute("factors");
		RIntegerVector response = terms.getIntegerAttribute("response");

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

				if(data.hasElement(variable)){
					return data.getVectorElement(variable);
				} // End if

				if((variable).equals(responseVariable)){
					return fitted.getVectorElement("(response)");
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

		FormulaUtil.setLabel(formula, terms, levels, encoder);

		FormulaUtil.addFeatures(formula, termColumns, false, encoder);
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector party = getObject();

		RGenericVector partyNode = party.getGenericElement("node");

		RGenericVector predicted = DecorationUtil.getGenericElement(party, "predicted");

		RVector<?> response = predicted.getVectorElement("(response)");
		RDoubleVector prob = predicted.getDoubleElement("(prob)", false);

		Node root = encodeNode(True.INSTANCE, partyNode, response, prob, schema);

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

	private Node encodeNode(Predicate predicate, RGenericVector partyNode, RVector<?> response, RDoubleVector prob, Schema schema){
		RIntegerVector id = partyNode.getIntegerElement("id");
		RGenericVector split = partyNode.getGenericElement("split");
		RGenericVector kids = partyNode.getGenericElement("kids");
		RGenericVector surrogates = partyNode.getGenericElement("surrogates");
		RGenericVector info = partyNode.getGenericElement("info");

		if(surrogates != null){
			throw new IllegalArgumentException();
		}

		Label label = schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		boolean factorResponse = RExpUtil.isFactor(response);

		Node result;

		if(factorResponse){
			result = new ClassifierNode(null, predicate);
		} else

		{
			if(kids == null){
				result = new LeafNode(null, predicate);
			} else

			{
				result = new BranchNode(null, predicate);
			}
		}

		result.setId(Integer.valueOf(id.asScalar()));

		if(factorResponse){
			RIntegerVector factor = (RIntegerVector)response;

			int index = id.asScalar() - 1;

			result.setScore(factor.getFactorValue(index));

			CategoricalLabel categoricalLabel = (CategoricalLabel)label;

			List<Double> probabilities = FortranMatrixUtil.getRow(prob.getValues(), response.size(), categoricalLabel.size(), index);

			List<ScoreDistribution> scoreDistributions = result.getScoreDistributions();

			for(int i = 0; i < categoricalLabel.size(); i++){
				Object value = categoricalLabel.getValue(i);
				Double probability = probabilities.get(i);

				ScoreDistribution scoreDistribution = new ScoreDistribution(value, probability);

				scoreDistributions.add(scoreDistribution);
			}
		} else

		{
			result.setScore(response.getValue(id.asScalar() - 1));
		} // End if

		if(kids == null){
			return result;
		}

		RIntegerVector varid = split.getIntegerElement("varid");
		RDoubleVector breaks = split.getDoubleElement("breaks");
		RIntegerVector index = split.getIntegerElement("index");
		RBooleanVector right = split.getBooleanElement("right");

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

			Double value = breaks.asScalar();

			if(right.asScalar()){
				leftPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.LESS_OR_EQUAL, value);
				rightPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.GREATER_THAN, value);
			} else

			{
				leftPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.LESS_THAN, value);
				rightPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.GREATER_OR_EQUAL, value);
			}

			Node leftChild = encodeNode(leftPredicate, (RGenericVector)kids.getValue(0), response, prob, schema);
			Node rightChild = encodeNode(rightPredicate, (RGenericVector)kids.getValue(1), response, prob, schema);

			result.addNodes(leftChild, rightChild);
		} else

		if(breaks == null && index != null){
			CategoricalFeature categoricalFeature = (CategoricalFeature)feature;

			if(kids.size() < 2){
				throw new IllegalArgumentException();
			}

			List<?> values = categoricalFeature.getValues();

			for(int i = 0; i < kids.size(); i++){
				Predicate childPredicate;

				if(right.asScalar()){
					childPredicate = createSimpleSetPredicate(categoricalFeature, selectValues(values, index, i + 1));
				} else

				{
					throw new IllegalArgumentException();
				}

				Node child = encodeNode(childPredicate, (RGenericVector)kids.getValue(i), response, prob, schema);

				result.addNodes(child);
			}
		} else

		{
			throw new IllegalArgumentException();
		}

		return result;
	}

	static
	private List<Object> selectValues(List<?> values, RIntegerVector index, int flag){
		List<Object> result = new ArrayList<>();

		if(values.size() != index.size()){
			throw new IllegalArgumentException();
		}

		for(int i = 0; i < values.size(); i++){
			Object value = values.get(i);

			if(index.getValue(i) == flag){
				result.add(value);
			}
		}

		return result;
	}
}
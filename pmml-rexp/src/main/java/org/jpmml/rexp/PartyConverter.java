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
import org.dmg.pmml.ScoreProbability;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.BranchNode;
import org.dmg.pmml.tree.ClassifierNode;
import org.dmg.pmml.tree.LeafNode;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.DiscreteFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.MissingLabelException;
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

				if(data instanceof RFactorVector){
					RFactorVector factor = (RFactorVector)data;

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

		RFactorVector levels = null;

		if(responseIndex != 0){
			RVector<?> responseData = context.getData(responseVariable);

			if(responseData instanceof RFactorVector){
				levels = (RFactorVector)responseData;
			}
		} else

		{
			throw new MissingLabelException();
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

		Node root = encodeNode(partyNode, True.INSTANCE, response, prob, schema);

		TreeModel treeModel;

		if(response instanceof RFactorVector){
			CategoricalLabel categoricalLabel = schema.requireCategoricalLabel();

			treeModel = new TreeModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel), root)
				.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));
		} else

		{
			treeModel = new TreeModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema), root);
		}

		return treeModel;
	}

	private Node encodeNode(RGenericVector partyNode, Predicate predicate, RVector<?> response, RDoubleVector prob, Schema schema){
		RIntegerVector id = partyNode.getIntegerElement("id");
		RGenericVector split = partyNode.getGenericElement("split");
		RGenericVector kids = partyNode.getGenericElement("kids");
		RGenericVector surrogates = partyNode.getGenericElement("surrogates");
		RGenericVector info = partyNode.getGenericElement("info");

		if(surrogates != null){
			throw new RExpException("Surrogate splits are not supported");
		}

		List<? extends Feature> features = schema.getFeatures();

		Node result;

		if(response instanceof RFactorVector){
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

		if(response instanceof RFactorVector){
			RFactorVector factor = (RFactorVector)response;

			int index = id.asScalar() - 1;

			result.setScore(factor.getFactorValue(index));

			CategoricalLabel categoricalLabel = schema.requireCategoricalLabel();

			List<Double> probabilities = FortranMatrixUtil.getRow(prob.getValues(), response.size(), categoricalLabel.size(), index);

			List<ScoreDistribution> scoreDistributions = result.getScoreDistributions();

			for(int i = 0; i < categoricalLabel.size(); i++){
				Object value = categoricalLabel.getValue(i);
				Double probability = probabilities.get(i);

				ScoreDistribution scoreDistribution = new ScoreProbability(value, null, probability);

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

			kids.checkSize(2);

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

			Node leftChild = encodeNode(kids.getGenericValue(0), leftPredicate, response, prob, schema);
			Node rightChild = encodeNode(kids.getGenericValue(1), rightPredicate, response, prob, schema);

			result.addNodes(leftChild, rightChild);
		} else

		if(breaks == null && index != null){
			DiscreteFeature discreteFeature = (DiscreteFeature)feature;

			kids.checkMinSize(2);

			List<?> values = discreteFeature.getValues();

			for(int i = 0; i < kids.size(); i++){
				Predicate childPredicate;

				if(right.asScalar()){
					childPredicate = createPredicate(discreteFeature, selectValues(values, index, i + 1));
				} else

				{
					throw new IllegalArgumentException();
				}

				Node child = encodeNode(kids.getGenericValue(i), childPredicate, response, prob, schema);

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

		index.checkSize(values.size());

		for(int i = 0; i < values.size(); i++){
			Object value = values.get(i);

			if(index.getValue(i) == flag){
				result.add(value);
			}
		}

		return result;
	}
}